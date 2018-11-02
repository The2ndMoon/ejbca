/*************************************************************************
 *                                                                       *
 *  EJBCA - Proprietary Modules: Enterprise Certificate Authority        *
 *                                                                       *
 *  Copyright (c), PrimeKey Solutions AB. All rights reserved.           *
 *  The use of the Proprietary Modules are subject to specific           * 
 *  commercial license terms.                                            *
 *                                                                       *
 *************************************************************************/

package org.ejbca.scp.publisher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.cesecore.util.CertTools;
import org.cesecore.util.StringTools;
import org.ejbca.core.model.ca.publisher.CustomPublisherProperty;
import org.ejbca.core.model.ca.publisher.CustomPublisherUiBase;
import org.ejbca.core.model.ca.publisher.ICustomPublisher;
import org.ejbca.core.model.ca.publisher.PublisherConnectionException;
import org.ejbca.core.model.ca.publisher.PublisherException;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * This class is used for publishing certificates and CRLs to a remote destination over scp. 
 * 
 * @version $Id$
 */
public class ScpPublisher extends CustomPublisherUiBase implements ICustomPublisher {

    private static final long serialVersionUID = 1L;
    private static Logger log = Logger.getLogger(ScpPublisher.class);
    public static final String ANONYMIZE_CERTIFICATES_PROPERTY_NAME = "anonymize.certificates";

    public static final String SSH_USERNAME = "ssh.username";
    public static final String CRL_SCP_DESTINATION_PROPERTY_NAME = "crl.scp.destination";
    public static final String CERT_SCP_DESTINATION_PROPERTY_NAME = "cert.scp.destination";
    public static final String SCP_PRIVATE_KEY_PASSWORD = "scp.privatekey.password";
    public static final String SCP_PRIVATE_KEY_PROPERTY_NAME = "scp.privatekey";

    public static final String SCP_KNOWN_HOSTS_PROPERTY_NAME = "scp.knownhosts";

    private static final String EKU_PKIX_OCSPSIGNING = "1.3.6.1.5.5.7.3.9";

    private boolean anonymizeCertificates;

    private String crlSCPDestination = null;
    private String certSCPDestination = null;
    private String scpPrivateKey = null;
    private String scpKnownHosts = null;
    private String sshUsername = null;
    private String privateKeyPassword = null;
    
    

    public ScpPublisher() {
    }

    /**
     * Load used properties.
     * 
     * @param properties
     *            The properties to load.
     * 
     * @see org.ejbca.core.model.ca.publisher.ICustomPublisher#init(java.util.Properties)
     */
    @Override
    public void init(Properties properties) {
        if (log.isTraceEnabled()) {
            log.trace(">init");
        }
        anonymizeCertificates = getBooleanProperty(properties, ANONYMIZE_CERTIFICATES_PROPERTY_NAME);
        crlSCPDestination = getProperty(properties, CRL_SCP_DESTINATION_PROPERTY_NAME);
        certSCPDestination = getProperty(properties, CERT_SCP_DESTINATION_PROPERTY_NAME);
        scpPrivateKey = getProperty(properties, SCP_PRIVATE_KEY_PROPERTY_NAME);
        scpKnownHosts = getProperty(properties, SCP_KNOWN_HOSTS_PROPERTY_NAME);
        sshUsername = getProperty(properties, SSH_USERNAME);
        String encryptedPassword = getProperty(properties, SCP_PRIVATE_KEY_PASSWORD);
        //Password is encrypted on the database, using the key password.encryption.key
        if (StringUtils.isNotEmpty(encryptedPassword)) {
            try {
                privateKeyPassword = StringTools.pbeDecryptStringWithSha256Aes192(encryptedPassword);
            } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException e) {
                throw new IllegalStateException("Could not decrypt encoded private key password.", e);
            }
        } else {
            privateKeyPassword = "";
        }
        addProperty(new CustomPublisherProperty(ANONYMIZE_CERTIFICATES_PROPERTY_NAME, CustomPublisherProperty.UI_BOOLEAN,
                Boolean.valueOf(anonymizeCertificates).toString()));
        addProperty(new CustomPublisherProperty(SSH_USERNAME, CustomPublisherProperty.UI_TEXTINPUT, sshUsername));
        addProperty(new CustomPublisherProperty(CRL_SCP_DESTINATION_PROPERTY_NAME, CustomPublisherProperty.UI_TEXTINPUT, crlSCPDestination));
        addProperty(new CustomPublisherProperty(CERT_SCP_DESTINATION_PROPERTY_NAME, CustomPublisherProperty.UI_TEXTINPUT, certSCPDestination));
        addProperty(new CustomPublisherProperty(SCP_PRIVATE_KEY_PROPERTY_NAME, CustomPublisherProperty.UI_TEXTINPUT, scpPrivateKey));
        addProperty(new CustomPublisherProperty(SCP_PRIVATE_KEY_PASSWORD, CustomPublisherProperty.UI_TEXTINPUT_PASSWORD, scpPrivateKey));
        addProperty(new CustomPublisherProperty(SCP_KNOWN_HOSTS_PROPERTY_NAME, CustomPublisherProperty.UI_TEXTINPUT, scpKnownHosts));

    }


    private String getProperty(Properties properties, String propertyName) {
        String property = properties.getProperty(propertyName);
        if (property == null) {
            return "";
        } else {
            return property;
        }
    }

    private boolean getBooleanProperty(Properties properties, String propertyName) {
        String property = getProperty(properties, propertyName);
        if (property.equalsIgnoreCase("true")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Writes certificate to temporary file and executes an external command
     * with the full pathname of the temporary file as argument. The temporary
     * file is the encoded form of the certificate e.g. X.509 certificates would
     * be encoded as ASN.1 DER. All parameters but incert are ignored.
     * 
     * @param incert
     *            The certificate
     * @param username
     *            The username
     * @param type
     *            The certificate type
     * 
     * @see org.ejbca.core.model.ca.publisher.ICustomPublisher#storeCertificate(org.ejbca.core.model.log.Admin,
     *      java.security.cert.Certificate, java.lang.String, java.lang.String,
     *      int, int)
     */
    @Override
    public boolean storeCertificate(AuthenticationToken admin, Certificate incert, String username, String password, String userDN, String cafp,
            int status, int type, long revocationDate, int revocationReason, String tag, int certificateProfileId, long lastUpdate,
            ExtendedInformation extendedinformation) throws PublisherException {
        if (log.isTraceEnabled()) {
            log.trace(">storeCertificate, Storing Certificate for user: " + username);
        }
        if ((status == CertificateConstants.CERT_REVOKED) || (status == CertificateConstants.CERT_ACTIVE)) {
            // Don't publish non-active certificates
            try {
                byte[] certBlob = incert.getEncoded();
                X509Certificate x509cert = (X509Certificate) incert;
                String fingerprint = CertTools.getFingerprintAsString(certBlob);
                String issuerDN = CertTools.getIssuerDN(incert);
                String serialNumber = x509cert.getSerialNumber().toString();
                String subjectDN = CertTools.getSubjectDN(incert);
                boolean anon = anonymizeCertificates && type == CertificateConstants.CERTTYPE_ENDENTITY;
                if (anon) {
                    List<String> ekus = x509cert.getExtendedKeyUsage();
                    if (ekus != null)
                        for (String eku : ekus) {
                            if (eku.equals(EKU_PKIX_OCSPSIGNING)) {
                                anon = false;
                            }
                        }
                }
                BlobWriter bw = new BlobWriter();
                // Now write the object..
                // MUST be in the same order as read by the reader!
                bw.putString(fingerprint).putString(issuerDN).putString(serialNumber).putString(anon ? "anonymized" : subjectDN)
                        .putArray(anon ? null : certBlob).putInt(type).putInt(status).putLong(revocationDate).putInt(revocationReason)
                        .putLong(lastUpdate).putInt(certificateProfileId).putLong(x509cert.getNotAfter().getTime());
                final String fileName = fingerprint + ".cer";
                performScp(fileName, sshUsername, bw.getTotal(), certSCPDestination, scpPrivateKey, privateKeyPassword, scpKnownHosts);
            } catch (GeneralSecurityException | IOException | JSchException e) {
                String msg = e.getMessage();
                log.error(msg);
                throw new PublisherException(msg, e);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("<storeCertificate");
        }
        return true;
    }

    /**
     * Writes the CRL to a temporary file and executes an external command with
     * the temporary file as argument. By default, a PublisherException is
     * thrown if the external command returns with an errorlevel or outputs to
     * stderr.
     * 
     * @see org.ejbca.core.model.ca.publisher.ICustomPublisher#storeCRL(org.ejbca.core.model.log.Admin,
     *      byte[], java.lang.String, int)
     */
    @Override
    public boolean storeCRL(AuthenticationToken admin, byte[] incrl, String cafp, int number, String userDN) throws PublisherException {
        if (log.isTraceEnabled()) {
            log.trace(">storeCRL, Storing CRL");
        }
        String fileName = CertTools.getFingerprintAsString(incrl) + ".crl";
        try {
            performScp(fileName, sshUsername, incrl, crlSCPDestination, scpPrivateKey, privateKeyPassword, scpKnownHosts);
        } catch (JSchException | IOException e) {
            String msg = e.getMessage();
            log.error(msg == null ? "Unknown error" : msg, e);
            throw new PublisherException(msg);
        }

        if (log.isTraceEnabled()) {
            log.trace("<storeCRL");
        }
        return true;
    }

    /**
     * 
     * 
     * @see org.ejbca.core.model.ca.publisher.ICustomPublisher#testConnection()
     */
    @Override
    public void testConnection() throws PublisherConnectionException {

    }

    @Override
    public boolean willPublishCertificate(int status, int revocationReason) {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Copies the given file to the destination over SCP 
     * 
     * @param destinationFileName The filename at the destination
     * @param username the username connected to the private key
     * @param password the password required to unlock the private key. May be null if the private key is not locked. 
     * @param data a byte array containing the data to be written
     * @param destination the full path to the destination in the format host:path
     * @param privateKeyPath path to the local private key. This is also used as the identifying name of the key. The corresponding public key is 
     *  assumed to be in a file with the same name with suffix .pub.
     * @param knownHostsFile the path to the .hosts file in the system
     * @throws JSchException if an SSH connection could not be established
     * @throws IOException if the file could not be written over the channel 
     */
    private void performScp(final String destinationFileName, final String username, final byte[] data, String destination,
            final String privateKeyPath, final String privateKeyPassword, final String knownHostsFile) throws JSchException, IOException {
        if(!(new File(privateKeyPath)).exists()) {
            throw new IllegalArgumentException("Private key file " + privateKeyPath + " was not found");
        }
        if(!(new File(knownHostsFile)).exists()) {
            throw new IllegalArgumentException("Hosts file " + knownHostsFile + " was not found");
        }
        
        destination = destination.substring(destination.indexOf('@') + 1);
        String host = destination.substring(0, destination.indexOf(':'));
        String rfile = destination.substring(destination.indexOf(':') + 1);
        JSch jsch = new JSch();
        if (privateKeyPassword != null) {
            jsch.addIdentity(privateKeyPath, privateKeyPassword);
        } else {
            jsch.addIdentity(privateKeyPath);
        }
        jsch.setKnownHosts(knownHostsFile);
        Session session = jsch.getSession(username, host, 22);
        
       // java.util.Properties config = new java.util.Properties(); 
       // config.put("StrictHostKeyChecking", "no");
       // session.setConfig(config);
        
        session.connect();
        // exec 'scp -t rfile' remotely
        String command = "scp -p -t " + rfile;
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);
        // get I/O streams for remote scp
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();
        channel.connect();
        checkAck(in);
        // send "C0644 filesize filename", where filename should not include '/'
        long filesize = data.length;
        command = "C0644 " + filesize + " " + destinationFileName + "\n";
        out.write(command.getBytes());
        out.flush();
        checkAck(in);
        byte[] buf = new byte[1024];
        out.write(data);
        // send '\0'
        buf[0] = 0;
        out.write(buf, 0, 1);
        out.flush();
        checkAck(in);
        out.close();
        channel.disconnect();
        session.disconnect();
    }

    private void checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        // 1 for error,
        // 2 for fatal error,
        // -1
        if (b <= 0)
            return;
        StringBuffer sb = new StringBuffer();
        int c;
        do {
            c = in.read();
            sb.append((char) c);
        } while (c != '\n');
        throw new IOException("SCP error: " + sb.toString());
    }

    private static class BlobWriter {
        private ByteArrayOutputStream baos = new ByteArrayOutputStream();

        BlobWriter putString(String string) throws IOException {
            return putArray(string.getBytes("UTF-8"));
        }

        BlobWriter putArray(byte[] array) throws IOException {
            if (array == null) {
                return putShort(0);
            }
            putShort(array.length);
            baos.write(array);
            return this;
        }

        BlobWriter putShort(int value) {
            baos.write((byte) (value >>> 8));
            baos.write((byte) value);
            return this;
        }

        BlobWriter putInt(int value) {
            putShort(value >>> 16);
            putShort(value);
            return this;
        }

        BlobWriter putLong(long value) {
            putInt((int) (value >>> 32));
            putInt((int) value);
            return this;
        }

        byte[] getTotal() {
            return baos.toByteArray();
        }

    }
}