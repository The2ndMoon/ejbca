/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.core.protocol.cmp;

import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DEROutputStream;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CaSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.ejb.upgrade.ConfigurationSessionRemote;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.protocol.FailInfo;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.InterfaceCache;
import org.ejbca.util.keystore.KeyTools;

import com.novosec.pkix.asn1.cmp.PKIMessage;

/**
 * @author tomas
 * @version $Id$
 */
public class CrmfRARequestTest extends CmpTestCase {

    final private static Logger log = Logger.getLogger(CrmfRARequestTest.class);

    final private static String PBEPASSWORD = "password";

    final private String issuerDN;

    final private int caid;
    final private Admin admin;
    final private X509Certificate cacert;

    private CaSessionRemote caSession = InterfaceCache.getCaSession();
    private CAAdminSessionRemote caAdminSessionRemote = InterfaceCache.getCAAdminSession();
    private ConfigurationSessionRemote configurationSession = InterfaceCache.getConfigurationSession();
    private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();

    public CrmfRARequestTest(String arg0) throws CertificateEncodingException, CertificateException, RemoteException {
        super(arg0);
        // Configure CMP for this test
        configurationSession.updateProperty(CmpConfiguration.CONFIG_OPERATIONMODE, "ra");
        configurationSession.updateProperty(CmpConfiguration.CONFIG_ALLOWRAVERIFYPOPO, "true");
        configurationSession.updateProperty(CmpConfiguration.CONFIG_RESPONSEPROTECTION, "signature");
        configurationSession.updateProperty(CmpConfiguration.CONFIG_RA_AUTHENTICATIONSECRET, "password");
        log.info("Current server configuration:");
        log.info("    " + CmpConfiguration.CONFIG_ALLOWRAVERIFYPOPO + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_ALLOWRAVERIFYPOPO, null));
        log.info("    " + CmpConfiguration.CONFIG_DEFAULTCA + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_DEFAULTCA, null));
        log.info("    " + CmpConfiguration.CONFIG_OPERATIONMODE + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_OPERATIONMODE, null));
        log.info("    " + CmpConfiguration.CONFIG_RA_AUTHENTICATIONSECRET + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_RA_AUTHENTICATIONSECRET, null));
        log.info("    " + CmpConfiguration.CONFIG_RA_CERTIFICATEPROFILE + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_RA_CERTIFICATEPROFILE, null));
        log.info("    " + CmpConfiguration.CONFIG_RA_ENDENTITYPROFILE + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_RA_ENDENTITYPROFILE, null));
        log.info("    " + CmpConfiguration.CONFIG_RACANAME + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_RACANAME, null));
        log.info("    " + CmpConfiguration.CONFIG_RESPONSEPROTECTION + ": " + configurationSession.getProperty(CmpConfiguration.CONFIG_RESPONSEPROTECTION, null));

        admin = new Admin(Admin.TYPE_BATCHCOMMANDLINE_USER);
        CryptoProviderTools.installBCProvider();
        // Try to use AdminCA1 if it exists
        final CAInfo adminca1;

        adminca1 = caAdminSessionRemote.getCAInfo(admin, "AdminCA1");

        if (adminca1 == null) {
            final Collection<Integer> caids;

            caids = caSession.getAvailableCAs(admin);

            final Iterator<Integer> iter = caids.iterator();
            int tmp = 0;
            while (iter.hasNext()) {
                tmp = iter.next().intValue();
            }
            caid = tmp;
        } else {
            caid = adminca1.getCAId();
        }
        if (caid == 0) {
            assertTrue("No active CA! Must have at least one active CA to run tests!", false);
        }
        final CAInfo cainfo;

        cainfo = caAdminSessionRemote.getCAInfo(admin, caid);

        Collection<Certificate> certs = cainfo.getCertificateChain();
        if (certs.size() > 0) {
            Iterator<Certificate> certiter = certs.iterator();
            Certificate cert = certiter.next();
            String subject = CertTools.getSubjectDN(cert);
            if (StringUtils.equals(subject, cainfo.getSubjectDN())) {
                // Make sure we have a BC certificate
                try {
                    cacert = (X509Certificate) CertTools.getCertfromByteArray(cert.getEncoded());
                } catch (Exception e) {
                    throw new Error(e);
                }
            } else {
                cacert = null;
            }
        } else {
            log.error("NO CACERT for caid " + caid);
            cacert = null;
        }
        issuerDN = cacert != null ? cacert.getIssuerDN().getName() : "CN=AdminCA1,O=EJBCA Sample,C=SE";
    }

    /**
     * @param userDN
     *            for new certificate.
     * @param keys
     *            key of the new certificate.
     * @param sFailMessage
     *            if !=null then EJBCA is expected to fail. The failure response
     *            message string is checked against this parameter.
     * @throws Exception
     */
    private void crmfHttpUserTest(String userDN, KeyPair keys, String sFailMessage) throws Exception {

        // Create a new good user

        final byte[] nonce = CmpMessageHelper.createSenderNonce();
        final byte[] transid = CmpMessageHelper.createSenderNonce();
        final int reqId;
        {
            final PKIMessage one = genCertReq(issuerDN, userDN, keys, cacert, nonce, transid, true, null, null, null);
            final PKIMessage req = protectPKIMessage(one, false, PBEPASSWORD, 567);

            reqId = req.getBody().getIr().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
            assertNotNull(req);
            final ByteArrayOutputStream bao = new ByteArrayOutputStream();
            final DEROutputStream out = new DEROutputStream(bao);
            out.writeObject(req);
            final byte[] ba = bao.toByteArray();
            // Send request and receive response
            final byte[] resp = sendCmpHttp(ba, 200);
            assertNotNull(resp);
            assertTrue(resp.length > 0);
            // do not check signing if we expect a failure (sFailMessage==null)
            checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, sFailMessage == null, false);
            if (sFailMessage == null) {
                checkCmpCertRepMessage(userDN, cacert, resp, reqId);
            } else {
                checkCmpFailMessage(resp, sFailMessage, CmpPKIBodyConstants.ERRORMESSAGE, reqId, FailInfo.BAD_REQUEST.hashCode());
            }
        }
        {
            // Send a confirm message to the CA
            final String hash = "foo123";
            final PKIMessage con = genCertConfirm(userDN, cacert, nonce, transid, hash, reqId);
            assertNotNull(con);
            PKIMessage confirm = protectPKIMessage(con, false, PBEPASSWORD, 567);
            final ByteArrayOutputStream bao = new ByteArrayOutputStream();
            final DEROutputStream out = new DEROutputStream(bao);
            out.writeObject(confirm);
            final byte[] ba = bao.toByteArray();
            // Send request and receive response
            final byte[] resp = sendCmpHttp(ba, 200);
            assertNotNull(resp);
            assertTrue(resp.length > 0);
            checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, false);
            checkCmpPKIConfirmMessage(userDN, cacert, resp);
        }
    }

    public void test01CrmfHttpOkUser() throws Exception {
        final CAInfo caInfo = caAdminSessionRemote.getCAInfo(admin, "AdminCA1");
        // make sure same keys for different users is prevented
        caInfo.setDoEnforceUniquePublicKeys(true);
        // make sure same DN for different users is prevented
        caInfo.setDoEnforceUniqueDistinguishedName(true);
        caAdminSessionRemote.editCA(admin, caInfo);

        final KeyPair key1 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final KeyPair key2 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final KeyPair key3 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final KeyPair key4 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        final String userName1 = "cmptest1";
        final String userName2 = "cmptest2";
        final String userDN1 = "C=SE,O=PrimeKey,CN=" + userName1;
        final String userDN2 = "C=SE,O=PrimeKey,CN=" + userName2;
        // check that several certificates could be created for one user and one
        // key.
        crmfHttpUserTest(userDN1, key1, null);
        crmfHttpUserTest(userDN2, key2, null);
        // check that the request fails when asking for certificate for another
        // user with same key.
        crmfHttpUserTest(userDN2, key1, InternalResources.getInstance().getLocalizedMessage("signsession.key_exists_for_another_user", "'" + userName2 + "'",
                "'" + userName1 + "'"));
        crmfHttpUserTest(userDN1, key2, InternalResources.getInstance().getLocalizedMessage("signsession.key_exists_for_another_user", "'" + userName1 + "'",
                "'" + userName2 + "'"));
        // check that you can not issue a certificate with same DN as another
        // user.
        crmfHttpUserTest("CN=AdminCA1,O=EJBCA Sample,C=SE", key3, InternalResources.getInstance().getLocalizedMessage(
                "signsession.subjectdn_exists_for_another_user", "'AdminCA1'", "'SYSTEMCA'"));
        String hostname;

        hostname = configurationSession.getProperty(WebConfiguration.CONFIG_HTTPSSERVERHOSTNAME, "localhost");

        crmfHttpUserTest("CN=" + hostname + ",O=EJBCA Sample,C=SE", key4, InternalResources.getInstance().getLocalizedMessage(
                "signsession.subjectdn_exists_for_another_user", "'" + hostname + "'", "'tomcat'"));

        userAdminSession.deleteUser(admin, userName1);
        userAdminSession.deleteUser(admin, userName2);
        try {
            userAdminSession.deleteUser(admin, "AdminCA1");
        } catch (NotFoundException e) {
        }
        try {
            userAdminSession.deleteUser(admin, hostname);
        } catch (NotFoundException e) {
        }
    }

    public void testZZZCleanUp() throws Exception {
    	log.trace(">testZZZCleanUp");
        assertTrue("Unable to restore server configuration.", configurationSession.restoreConfiguration());
    	log.trace("<testZZZCleanUp");
    }
}
