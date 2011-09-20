/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.certificates.crl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509KeyUsage;
import org.cesecore.RoleUsingTestCase;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.authorization.rules.AccessRuleData;
import org.cesecore.authorization.rules.AccessRuleState;
import org.cesecore.certificates.ca.CA;
import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CAOfflineException;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.CaSessionTest;
import org.cesecore.certificates.ca.CaTestSessionRemote;
import org.cesecore.certificates.ca.X509CAInfo;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificate.CertificateInfo;
import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.certificates.certificate.InternalCertificateStoreSessionRemote;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.certificates.util.cert.CrlExtensions;
import org.cesecore.jndi.JndiHelper;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.roles.RoleData;
import org.cesecore.roles.access.RoleAccessSessionRemote;
import org.cesecore.roles.management.RoleManagementSessionRemote;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests CRL create session.
 * 
 * Based on EJBCA version: CreateCRLSessionTest.java 11375 2011-02-21 12:25:48Z anatom
 * 
 * @version $Id$
 */
public class CrlCreateSessionCRLTest extends RoleUsingTestCase {

    private final static Logger log = Logger.getLogger(CrlCreateSessionCRLTest.class);

    private static final String X509CADN = "CN=CRLTEST";
    private static CA testx509ca;

    private static final String USERNAME = "crltest";

    private CaSessionRemote caSession = JndiHelper.getRemoteSession(CaSessionRemote.class);
    private CaTestSessionRemote caTestSession = JndiHelper.getRemoteSession(CaTestSessionRemote.class);
    private RoleAccessSessionRemote roleAccessSession = JndiHelper.getRemoteSession(RoleAccessSessionRemote.class);
    private RoleManagementSessionRemote roleManagementSession = JndiHelper.getRemoteSession(RoleManagementSessionRemote.class);

    private CertificateStoreSessionRemote certificateStoreSession = JndiHelper.getRemoteSession(CertificateStoreSessionRemote.class);
    private CrlStoreSessionRemote crlStoreSession = JndiHelper.getRemoteSession(CrlStoreSessionRemote.class);
    private CrlCreateSessionRemote crlCreateSession = JndiHelper.getRemoteSession(CrlCreateSessionRemote.class);
    private InternalCertificateStoreSessionRemote internalCertificateStoreSession = JndiHelper
            .getRemoteSession(InternalCertificateStoreSessionRemote.class);

    private static KeyPair keys;

    @BeforeClass
    public static void createProvider() throws Exception {
        CryptoProviderTools.installBCProvider();
        testx509ca = CaSessionTest.createTestX509CA(X509CADN, null, false);
        keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
    }

    @Before
    public void setUp() throws Exception {
        // Set up base role that can edit roles
        setUpAuthTokenAndRole("CrlCreateSessionTest");

        // Now we have a role that can edit roles, we can edit this role to include more privileges
        RoleData role = roleAccessSession.findRole("CrlCreateSessionTest");

        List<AccessRuleData> accessRules = new ArrayList<AccessRuleData>();
        accessRules.add(new AccessRuleData(role.getRoleName(), StandardRules.CAADD.resource(), AccessRuleState.RULE_ACCEPT, true));
        accessRules.add(new AccessRuleData(role.getRoleName(), StandardRules.CAEDIT.resource(), AccessRuleState.RULE_ACCEPT, true));
        accessRules.add(new AccessRuleData(role.getRoleName(), StandardRules.CAREMOVE.resource(), AccessRuleState.RULE_ACCEPT, true));
        accessRules.add(new AccessRuleData(role.getRoleName(), StandardRules.CAACCESSBASE.resource(), AccessRuleState.RULE_ACCEPT, true));
        accessRules.add(new AccessRuleData(role.getRoleName(), StandardRules.CREATECRL.resource(), AccessRuleState.RULE_ACCEPT, true));
        roleManagementSession.addAccessRulesToRole(roleMgmgToken, role, accessRules);

        // Remove any lingering testca before starting the tests
        caSession.removeCA(roleMgmgToken, testx509ca.getCAId());
        // Now add the test CA so it is available in the tests
        caSession.addCA(roleMgmgToken, testx509ca);

    }

    @After
    public void tearDown() throws Exception {
        // Remove any testca before exiting tests
        try {
            byte[] crl;
            while ((crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false)) != null) {
                X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
                internalCertificateStoreSession.removeCRL(roleMgmgToken, CertTools.getFingerprintAsString(x509crl));
            }
            while ((crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), true)) != null) {
                X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
                internalCertificateStoreSession.removeCRL(roleMgmgToken, CertTools.getFingerprintAsString(x509crl));
            }

            caSession.removeCA(roleMgmgToken, testx509ca.getCAId());
        } finally {
            // Be sure to to this, even if the above fails
            tearDownRemoveRole();
        }
    }

    /**
     * creates new crl
     */
    @Test
    public void testCreateNewCRL() throws Exception {
        crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
        X509CRL x509crl = null;

        // Get number of last CRL
        int number = crlStoreSession.getLastCRLNumber(testx509ca.getSubjectDN(), false);
        log.debug("Last CRLNumber = " + number);
        byte[] crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);
        x509crl = CertTools.getCRLfromByteArray(crl);

        BigInteger num = CrlExtensions.getCrlNumber(x509crl);
        // Create a new CRL again to see that the number increases
        crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
        int number1 = crlStoreSession.getLastCRLNumber(testx509ca.getSubjectDN(), false);
        assertEquals(number + 1, number1);
        byte[] crl1 = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
        X509CRL x509crl1 = CertTools.getCRLfromByteArray(crl1);
        BigInteger num1 = CrlExtensions.getCrlNumber(x509crl1);
        assertEquals(num.intValue() + 1, num1.intValue());

        /*
         * check revoked certificates
         */

        // Get number of last CRL
        Collection<RevokedCertInfo> revfp = certificateStoreSession.listRevokedCertInfo(testx509ca.getSubjectDN(), -1);
        log.debug("Number of revoked certificates=" + revfp.size());
        crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
        assertNotNull("Could not get CRL", crl);

        x509crl = CertTools.getCRLfromByteArray(crl);
        Set<? extends X509CRLEntry> revset = x509crl.getRevokedCertificates();
        // Revset will be null if there are no revoked certificates
        if (revset != null) {
            int revsize = revset.size();
            assertEquals(revfp.size(), revsize);
        } else {
            assertEquals(0, revfp.size());
        }

    }

    /**
     * Test revocation and reactivation of certificates
     * 
     */
    @Test
    public void testRevokeAndUnrevoke() throws Exception {

        X509Certificate cert = createCert();
        try {
            // Create a new CRL again...
            assertTrue(crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId()));
            // Check that our newly signed certificate is not present in a new CRL
            byte[] crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
            assertNotNull("Could not get CRL", crl);
            X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
            Set<? extends X509CRLEntry> revset = x509crl.getRevokedCertificates();
            if (revset != null) {
                Iterator<? extends X509CRLEntry> iter = revset.iterator();
                while (iter.hasNext()) {
                    X509CRLEntry ce = iter.next();
                    assertTrue(ce.getSerialNumber().compareTo(cert.getSerialNumber()) != 0);
                }
            } // If no revoked certificates exist at all, this test passed...

            certificateStoreSession.setRevokeStatus(roleMgmgToken, cert, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, null);
            // Create a new CRL again...
            assertTrue(crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId()));
            // Check that our newly signed certificate IS present in a new CRL
            crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
            assertNotNull("Could not get CRL", crl);
            x509crl = CertTools.getCRLfromByteArray(crl);
            revset = x509crl.getRevokedCertificates();
            assertNotNull(revset);
            Iterator<? extends X509CRLEntry> iter = revset.iterator();
            boolean found = false;
            while (iter.hasNext()) {
                X509CRLEntry ce = iter.next();
                if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                    found = true;
                    // TODO: verify the reason code
                }
            }
            assertTrue("Certificate with serial " + cert.getSerialNumber().toString(16) + " not revoked", found);

            // Unrevoke the certificate that we just revoked
            certificateStoreSession.setRevokeStatus(roleMgmgToken, cert, RevokedCertInfo.NOT_REVOKED, null);
            // Create a new CRL again...
            assertTrue(crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId()));
            // Check that our newly signed certificate IS NOT present in the new
            // CRL.
            crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
            assertNotNull("Could not get CRL", crl);
            x509crl = CertTools.getCRLfromByteArray(crl);
            revset = x509crl.getRevokedCertificates();
            if (revset != null) {
                iter = revset.iterator();
                found = false;
                while (iter.hasNext()) {
                    X509CRLEntry ce = iter.next();
                    if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                        found = true;
                    }
                }
                assertFalse(found);
            } // If no revoked certificates exist at all, this test passed...

            certificateStoreSession.setRevokeStatus(roleMgmgToken, cert, RevokedCertInfo.REVOCATION_REASON_CACOMPROMISE, null);
            assertTrue("Failed to revoke certificate!",
                    certificateStoreSession.isRevoked(CertTools.getIssuerDN(cert), CertTools.getSerialNumber(cert)));
            // Create a new CRL again...
            assertTrue(crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId()));
            // Check that our newly signed certificate IS present in a new CRL
            crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
            assertNotNull("Could not get CRL", crl);
            x509crl = CertTools.getCRLfromByteArray(crl);
            revset = x509crl.getRevokedCertificates();
            iter = revset.iterator();
            found = false;
            while (iter.hasNext()) {
                X509CRLEntry ce = (X509CRLEntry) iter.next();
                if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                    found = true;
                    // TODO: verify the reason code
                }
            }
            assertTrue(found);

            certificateStoreSession.setRevokeStatus(roleMgmgToken, cert, RevokedCertInfo.NOT_REVOKED, null);
            assertTrue("Was able to re-activate permanently revoked certificate!",
                    certificateStoreSession.isRevoked(CertTools.getIssuerDN(cert), CertTools.getSerialNumber(cert)));
            // Create a new CRL again...
            assertTrue(crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId()));
            // Check that our newly signed certificate is present in the new CRL,
            // because the revocation reason
            // was not CERTIFICATE_HOLD, we can only un-revoke certificates that are
            // on hold.
            crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false);
            assertNotNull("Could not get CRL", crl);
            x509crl = CertTools.getCRLfromByteArray(crl);
            revset = x509crl.getRevokedCertificates();
            iter = revset.iterator();
            found = false;
            while (iter.hasNext()) {
                X509CRLEntry ce = (X509CRLEntry) iter.next();
                if (ce.getSerialNumber().compareTo(cert.getSerialNumber()) == 0) {
                    found = true;
                }
            }
            assertTrue(found);
        } finally {
            internalCertificateStoreSession.removeCertificate(cert);
        }

    }

    /**
     * Test Overflow of CRL Period
     */
    @Test
    public void testCRLPeriodOverflow() throws Exception {
        log.trace(">test05CRLPeriodOverflow()");
        // Fetch CAInfo and save CRLPeriod
        CAInfo cainfo = testx509ca.getCAInfo();
        long tempCRLPeriod = cainfo.getCRLPeriod();
        X509Certificate cert = createCertWithValidity(1);
        try {
            // Revoke the user
            certificateStoreSession.setRevokeStatus(roleMgmgToken, cert, RevokedCertInfo.REVOCATION_REASON_KEYCOMPROMISE, null);
            // Change CRLPeriod
            cainfo.setCRLPeriod(Long.MAX_VALUE);
            caSession.editCA(roleMgmgToken, cainfo);
            // Create new CRL's
            assertTrue(crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId()));
            // Verify that status is not archived
            CertificateInfo certinfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(cert));
            assertFalse("Non Expired Revoked Certificate was archived", certinfo.getStatus() == CertificateConstants.CERT_ARCHIVED);
        } finally {
            internalCertificateStoreSession.removeCertificate(CertTools.getSerialNumber(cert));
            // Restore CRL Period
            cainfo.setCRLPeriod(tempCRLPeriod);
            caSession.editCA(roleMgmgToken, cainfo);
        }
    }

    /**
     * Tests the extension CRL Distribution Point on CRLs
     */
    @Test
    public void testCRLDistPointOnCRL() throws Exception {
        final String cdpURL = "http://www.ejbca.org/foo/bar.crl";
        X509CAInfo cainfo = (X509CAInfo) testx509ca.getCAInfo();
        X509CRL x509crl;
        byte[] cdpDER;

        cainfo.setUseCrlDistributionPointOnCrl(true);
        cainfo.setDefaultCRLDistPoint(cdpURL);
        caSession.editCA(roleMgmgToken, cainfo);
        crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
        x509crl = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), false));
        cdpDER = x509crl.getExtensionValue(X509Extensions.IssuingDistributionPoint.getId());
        assertNotNull("CRL has no distribution points", cdpDER);

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(cdpDER));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        IssuingDistributionPoint cdp = new IssuingDistributionPoint((ASN1Sequence) aIn.readObject());
        DistributionPointName distpoint = cdp.getDistributionPoint();

        assertEquals("CRL distribution point is different", cdpURL,
                ((DERIA5String) ((GeneralNames) distpoint.getName()).getNames()[0].getName()).getString());

        cainfo.setUseCrlDistributionPointOnCrl(false);
        cainfo.setDefaultCRLDistPoint("");
        caSession.editCA(roleMgmgToken, cainfo);
        crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
        x509crl = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), false));
        assertNull("CRL has distribution points", x509crl.getExtensionValue(X509Extensions.CRLDistributionPoints.getId()));
    }

    /**
     * Tests the extension Freshest CRL DP.
     */
    @Test
    public void testCRLFreshestCRL() throws Exception {
        final String cdpURL = "http://www.ejbca.org/foo/bar.crl";
        final String freshestCdpURL = "http://www.ejbca.org/foo/delta.crl";
        X509CAInfo cainfo = (X509CAInfo) testx509ca.getCAInfo();
        X509CRL x509crl;
        byte[] cFreshestDpDER;

        cainfo.setUseCrlDistributionPointOnCrl(true);
        cainfo.setDefaultCRLDistPoint(cdpURL);
        cainfo.setCADefinedFreshestCRL(freshestCdpURL);
        caSession.editCA(roleMgmgToken, cainfo);
        crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
        x509crl = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), false));
        cFreshestDpDER = x509crl.getExtensionValue(X509Extensions.FreshestCRL.getId());
        assertNotNull("CRL has no Freshest Distribution Point", cFreshestDpDER);

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(cFreshestDpDER));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        CRLDistPoint cdp = new CRLDistPoint((ASN1Sequence) aIn.readObject());
        DistributionPoint[] distpoints = cdp.getDistributionPoints();

        assertEquals("More CRL Freshest distributions points than expected", 1, distpoints.length);
        assertEquals("Freshest CRL distribution point is different", freshestCdpURL, ((DERIA5String) ((GeneralNames) distpoints[0]
                .getDistributionPoint().getName()).getNames()[0].getName()).getString());
    }

    @Test
    public void testCrlGenerateForAll() throws Exception {
        X509CAInfo cainfo = (X509CAInfo) testx509ca.getCAInfo();
        cainfo.setCRLIssueInterval(1); // Issue very often..
        cainfo.setDeltaCRLPeriod(1); // Issue very often..
        caSession.editCA(roleMgmgToken, cainfo);
        // make sure we have a CRL and delta CRL generated
        crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
        crlCreateSession.forceDeltaCRL(roleMgmgToken, testx509ca.getCAId());
        try {
            // Now wait and test again
            Thread.sleep(1000);
            final X509CRL x509crl = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), false));
            assertTrue(crlCreateSession.createCRLs(roleMgmgToken) > 0);
            final X509CRL x509crlAfter = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), false));
            assertTrue("Did not generate a newer CRL.", x509crlAfter.getThisUpdate().after(x509crl.getThisUpdate()));
            final X509CRL x509deltaCrl = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), true));
            assertTrue(crlCreateSession.createDeltaCRLs(roleMgmgToken) > 0);
            final X509CRL x509deltaCrlAfter = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), true));
            assertTrue("Did not generate a newer Delta CRL.", x509deltaCrlAfter.getThisUpdate().after(x509deltaCrl.getThisUpdate()));
            // Try a similar thing when we specify which CA IDs to generate CRLs for
            // Compare CRL numbers instead of Dates, since these CRLs might have been generated the same second as the last ones
            final Collection<Integer> caids = new ArrayList<Integer>();
            caids.add(Integer.valueOf(testx509ca.getCAId()));
            crlCreateSession.createCRLs(roleMgmgToken, caids, 2);
            final X509CRL x509crlAfter2 = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), false));
            assertTrue("Did not generate a newer CRL.",
                    CrlExtensions.getCrlNumber(x509crlAfter2).intValue() > CrlExtensions.getCrlNumber(x509crlAfter).intValue());
            crlCreateSession.createDeltaCRLs(roleMgmgToken, caids, 2);
            final X509CRL x509deltaCrlAfter2 = CertTools.getCRLfromByteArray(crlStoreSession.getLastCRL(cainfo.getSubjectDN(), true));
            assertTrue("Did not generate a newer Delta CRL.",
                    CrlExtensions.getCrlNumber(x509deltaCrlAfter2).intValue() > CrlExtensions.getCrlNumber(x509deltaCrlAfter).intValue());
        } finally {
            byte[] crl;
            while ((crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), false)) != null) {
                X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
                internalCertificateStoreSession.removeCRL(roleMgmgToken, CertTools.getFingerprintAsString(x509crl));
            }
            while ((crl = crlStoreSession.getLastCRL(testx509ca.getSubjectDN(), true)) != null) {
                X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
                internalCertificateStoreSession.removeCRL(roleMgmgToken, CertTools.getFingerprintAsString(x509crl));
            }
        }
    }

    /**
     * Test error handling of off-line CA during CRL creation.
     */
    @Test
    public void testCrlCreateSessionErrorHandling() throws Exception {
        CAInfo cainfo = testx509ca.getCAInfo();
        cainfo.setStatus(CAConstants.CA_OFFLINE);
        caSession.editCA(roleMgmgToken, cainfo);
        CA ca = caTestSession.getCA(roleMgmgToken, testx509ca.getCAId());
        assertEquals(CAConstants.CA_OFFLINE, ca.getStatus());
        assertEquals(CAConstants.CA_OFFLINE, ca.getCAInfo().getStatus());
        try {
            crlCreateSession.forceCRL(roleMgmgToken, testx509ca.getCAId());
            assertTrue("Trying to generate a CRL for CA with status CA_OFFLINE did not throw the CATokenOfflineException.", false);
        } catch (CAOfflineException e) {
            // Expected
        }
        cainfo.setStatus(CAConstants.CA_ACTIVE);
        caSession.editCA(roleMgmgToken, cainfo);
    }

    //
    // Helper methods
    //

    private X509Certificate createCert() throws Exception {
        return createCertWithValidity(10);
    }

    private X509Certificate createCertWithValidity(int validity) throws Exception {
        // Make user that we know...
        EndEntityInformation user = new EndEntityInformation(USERNAME, "C=SE,O=AnaTom,CN=crltest", testx509ca.getCAId(), null, "crltest@anatom.se",
                EndEntityConstants.USER_ENDUSER, 0, CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, EndEntityConstants.TOKEN_USERGEN, 0, null);
        // user that we know exists...
        CertificateProfile cp = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        int keyusage = X509KeyUsage.digitalSignature | X509KeyUsage.keyEncipherment;
        X509Certificate cert = (X509Certificate) testx509ca.generateCertificate(user, keys.getPublic(), keyusage, validity, cp, "00001");

        certificateStoreSession.storeCertificate(roleMgmgToken, cert, USERNAME, "1234", CertificateConstants.CERT_ACTIVE,
                CertificateConstants.CERTTYPE_ENDENTITY, CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, "footag", System.currentTimeMillis());
        assertNotNull("Failed to create certificate", cert);
        return cert;
    }

}
