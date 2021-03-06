/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.batchenrollmentgui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.cesecore.util.CertTools;
import org.ejbca.core.protocol.ws.client.gen.EjbcaWS;
import org.ejbca.core.protocol.ws.client.gen.EjbcaWSService;

/**
 * Dialog for connection and authentication settings.
 * 
 * @version $Id$
 */
public class ConnectDialog extends JDialog {

    private static final long serialVersionUID = -6727893196486472985L;

    /** Logger for this class. */
    private static final Logger LOG = Logger.getLogger(ConnectDialog.class);

    private static final String DEFAULT_URL = "https://localhost:8443/ejbca";
    private static final String WS_PATH = "/ejbcaws/ejbcaws?wsdl";

    private ConnectSettings settings;
    private EjbcaWS ejbcaWS;
    private static final File DEFAULT_CONNECT_FILE =
            new File("default_connect.properties");
    private static final File CONNECT_FILE = new File("connect.properties");

    private static final String TRUSTSTORE_TYPE_PEM = "PEM";
    private static final String TRUSTSTORE_TYPE_KEYSTORE = "Use keystore";

    private static final String[] TRUSTSTORE_TYPES = new String[] {
        TRUSTSTORE_TYPE_KEYSTORE,
        "JKS",
        "PKCS12",
        TRUSTSTORE_TYPE_PEM
    };

    /** Creates new form ConnectDialog. Uses the super-constructor that takes a Dialog object (rather than Frame), so the dialog appears in the taskbar. */
    public ConnectDialog(final java.awt.Dialog parent, final boolean modal) {
        super(parent, modal);
        initComponents();
        truststoreTypeComboBox.setModel(new DefaultComboBoxModel<String>(TRUSTSTORE_TYPES));
        if (CONNECT_FILE.exists()) {
            loadSettingsFromFile(CONNECT_FILE);
        } else {
            loadSettingsFromFile(DEFAULT_CONNECT_FILE);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        passwordPanel = new JPanel();
        passwordLabel = new JLabel();
        passwordField = new JPasswordField();
        jPanel1 = new JPanel();
        jLabel1 = new JLabel();
        urlTextField = new JTextField();
        jPanel2 = new JPanel();
        jLabel2 = new JLabel();
        truststoreFilePathTextField = new JTextField();
        truststoreTypeComboBox = new JComboBox<>();
        truststoreFilePathLabel = new JLabel();
        truststoreBrowseButton = new JButton();
        truststorePasswordLabel = new JLabel();
        truststorePasswordField = new JPasswordField();
        jPanel4 = new JPanel();
        jLabel8 = new JLabel();
        keystoreFilePathTextField = new JTextField();
        keystoreTypeComboBox = new JComboBox<>();
        jLabel9 = new JLabel();
        keystoreBrowseButton = new JButton();
        connectButton = new JButton();
        cancelButton = new JButton();
        defaultsButton = new JButton();

        passwordLabel.setText("Enter password:");

        passwordField.setText("jPasswordField1");

        GroupLayout passwordPanelLayout = new GroupLayout(passwordPanel);
        passwordPanel.setLayout(passwordPanelLayout);
        passwordPanelLayout.setHorizontalGroup(
            passwordPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(GroupLayout.Alignment.TRAILING, passwordPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(passwordPanelLayout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                    .addComponent(passwordField, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                    .addComponent(passwordLabel, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE))
                .addContainerGap())
        );
        passwordPanelLayout.setVerticalGroup(
            passwordPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(passwordPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(passwordLabel)
                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(passwordField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Connect to EJBCA");
        setLocationByPlatform(true);

        jPanel1.setBorder(BorderFactory.createTitledBorder("EJBCA"));

        jLabel1.setText("URL:");

        GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(urlTextField, GroupLayout.DEFAULT_SIZE, 474, Short.MAX_VALUE)
                    .addComponent(jLabel1, GroupLayout.PREFERRED_SIZE, 182, GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(urlTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel2.setBorder(BorderFactory.createTitledBorder("Truststore"));

        jLabel2.setText("Type:");

        truststoreTypeComboBox.setEditable(true);
        truststoreTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                truststoreTypeComboBoxActionPerformed(evt);
            }
        });

        truststoreFilePathLabel.setText("Truststore file path:");

        truststoreBrowseButton.setText("...");
        truststoreBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                truststoreBrowseButtonActionPerformed(evt);
            }
        });

        truststorePasswordLabel.setText("Password:");

        GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(truststorePasswordField, GroupLayout.DEFAULT_SIZE, 474, Short.MAX_VALUE)
                    .addComponent(truststoreFilePathLabel, GroupLayout.DEFAULT_SIZE, 474, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel2, GroupLayout.PREFERRED_SIZE, 208, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(truststoreTypeComboBox, 0, 254, Short.MAX_VALUE))
                    .addGroup(GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(truststoreFilePathTextField, GroupLayout.DEFAULT_SIZE, 432, Short.MAX_VALUE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(truststoreBrowseButton, GroupLayout.PREFERRED_SIZE, 36, GroupLayout.PREFERRED_SIZE))
                    .addComponent(truststorePasswordLabel, GroupLayout.PREFERRED_SIZE, 215, GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(truststoreTypeComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(truststoreFilePathLabel)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(truststoreFilePathTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(truststoreBrowseButton))
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(truststorePasswordLabel)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(truststorePasswordField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.setBorder(BorderFactory.createTitledBorder("Keystore"));

        jLabel8.setText("Type:");

        keystoreTypeComboBox.setEditable(true);
        keystoreTypeComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "JKS", "PKCS12", "Windows-MY", "PKCS11" }));

        jLabel9.setText("Keystore file path:");

        keystoreBrowseButton.setText("...");
        keystoreBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keystoreBrowseButtonActionPerformed(evt);
            }
        });

        GroupLayout jPanel4Layout = new GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel9, GroupLayout.DEFAULT_SIZE, 474, Short.MAX_VALUE)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel8, GroupLayout.PREFERRED_SIZE, 208, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(keystoreTypeComboBox, 0, 254, Short.MAX_VALUE))
                    .addGroup(GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                        .addComponent(keystoreFilePathTextField, GroupLayout.DEFAULT_SIZE, 432, Short.MAX_VALUE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(keystoreBrowseButton, GroupLayout.PREFERRED_SIZE, 36, GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(keystoreTypeComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(keystoreFilePathTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(keystoreBrowseButton))
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        connectButton.setText("Connect");
        connectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectButtonActionPerformed(evt);
            }
        });

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        defaultsButton.setText("Load defaults");
        defaultsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultsButtonActionPerformed(evt);
            }
        });

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel4, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, GroupLayout.Alignment.LEADING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(defaultsButton)
                        .addGap(18, 18, 18)
                        .addComponent(cancelButton)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(connectButton)))
                .addContainerGap())
        );

        layout.linkSize(SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, connectButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanel2, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jPanel4, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(connectButton)
                    .addComponent(cancelButton)
                    .addComponent(defaultsButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//NOPMD//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void connectButtonActionPerformed(java.awt.event.ActionEvent evt) {//NOPMD//GEN-FIRST:event_connectButtonActionPerformed
        settings = new ConnectSettings();
        settings.setUrl(urlTextField.getText());
        settings.setTruststoreType((String) truststoreTypeComboBox.getSelectedItem());
        settings.setTruststoreFile(truststoreFilePathTextField.getText());
        settings.setTruststorePassword(truststorePasswordField.getPassword());
        settings.setKeystoreType((String) keystoreTypeComboBox.getSelectedItem());
        settings.setKeystoreFile(keystoreFilePathTextField.getText());
//        settings.setKeystorePassword(keystorePasswordField.getPassword());

        try {
            Properties properties = new Properties();
            properties.put("url", settings.getUrl());
            properties.put("truststoreType", settings.getTruststoreType());
            properties.put("truststoreFile", settings.getTruststoreFile());
            properties.put("truststorePassword", new String(settings.getTruststorePassword()));
            properties.put("keystoreType", settings.getKeystoreType());
            properties.put("keystoreFile", settings.getKeystoreFile());
            properties.store(new FileOutputStream(CONNECT_FILE),
                    "Connect settings");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save configuration:\n"
                    + ex.getMessage(), "Connect", JOptionPane.WARNING_MESSAGE);
        }

        try {

            final String urlstr = settings.getUrl() + WS_PATH;

                KeyStore.CallbackHandlerProtection pp = new KeyStore.CallbackHandlerProtection(new CallbackHandler() {

                    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                        for (int i = 0; i < callbacks.length; i++) {
                            if (callbacks[i] instanceof PasswordCallback) {
                                final PasswordCallback pc = (PasswordCallback) callbacks[i];

                                passwordLabel.setText(pc.getPrompt());
                                passwordField.setText("");

                                JOptionPane.showMessageDialog(
                                        ConnectDialog.this, passwordPanel,
                                        "Connect", JOptionPane.PLAIN_MESSAGE);
                                if (passwordField.getPassword() != null) {
                                    pc.setPassword(passwordField.getPassword());
                                }
                            } else {
                                throw new UnsupportedCallbackException(callbacks[i],
                                        "Unrecognized Callback");
                            }
                        }
                    }
                });

                final KeyStore keystore;
                final KeyManagerFactory kKeyManagerFactory = KeyManagerFactory.getInstance("SunX509");

                if (settings.getKeystoreType().contains("Windows")) {
                    // CSP
                    keystore = getLoadedKeystoreCSP(settings.getKeystoreType(), pp);
                    kKeyManagerFactory.init(keystore, null);
                } else if (settings.getKeystoreType().equals("PKCS11")) {
                    // PKCS11
                    keystore = getLoadedKeystorePKCS11("PKCS11",
                            settings.getKeystoreFile(),
                            settings.getKeystorePassword(), pp);
                    kKeyManagerFactory.init(keystore, null);
                } else {
                    // PKCS12 must use BC as provider but not JKS
                    final String provider;
                    if (settings.getKeystoreType().equals("PKCS12")) {
                        provider = BouncyCastleProvider.PROVIDER_NAME;
                    } else {
                        provider = null;
                    }
                    
                    // Ask for password
                    char[] authcode;
                    passwordLabel.setText("Enter password for keystore:");
                    passwordField.setText("");
                    JOptionPane.showMessageDialog(
                            ConnectDialog.this, passwordPanel,
                            "Connect", JOptionPane.PLAIN_MESSAGE);
                    if (passwordField.getPassword() != null) {
                        authcode = passwordField.getPassword();
                    } else {
                        authcode = null;
                    }
    
                    // Other keystores for instance JKS
                    keystore = getLoadedKeystore(settings.getKeystoreFile(),
                            authcode,
                            settings.getKeystoreType(),
                            provider);
                 
                    // JKS has password on keys and need to be inited with password
                    if (settings.getKeystoreType().equals("JKS")) {
                        kKeyManagerFactory.init(keystore, authcode);
                    } else {
                        kKeyManagerFactory.init(keystore, null);
                    }
                }

                final KeyStore keystoreTrusted;
                if (TRUSTSTORE_TYPE_PEM.equals(settings.getTruststoreType())) {
                    keystoreTrusted = KeyStore.getInstance("JKS");
                    keystoreTrusted.load(null, null);
                    final Collection<Certificate> certs = CertTools.getCertsFromPEM(
                            new FileInputStream(settings.getTruststoreFile()), Certificate.class);
                    int i = 0;
                    for (Object o : certs) {
                        if (o instanceof Certificate) {
                            keystoreTrusted.setCertificateEntry("cert-" + i,
                                    (Certificate) o);
                            i++;
                        }
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Loaded " + i + " certs to truststore");
                    }
                } else if (TRUSTSTORE_TYPE_KEYSTORE.equals(
                        settings.getTruststoreType())) {
                    keystoreTrusted = KeyStore.getInstance("JKS");
                    keystoreTrusted.load(null, null);
                    final Enumeration<String> aliases = keystore.aliases();
                    int i = 0;
                    while(aliases.hasMoreElements()) {
                        final String alias = aliases.nextElement();
                        if (keystore.isCertificateEntry(alias)) {
                            keystoreTrusted.setCertificateEntry(alias,
                                    keystore.getCertificate(alias));
                            i++;
                        }
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Loaded " + i + " certs to truststore");
                    }
                } else {
                    keystoreTrusted = KeyStore.getInstance(settings.getTruststoreType());
                    keystoreTrusted.load(new FileInputStream(settings.getTruststoreFile()), settings.getTruststorePassword());
                }

                final TrustManagerFactory tTrustManagerFactory = TrustManagerFactory.getInstance("SunX509");
                tTrustManagerFactory.init(keystoreTrusted);

                KeyManager[] keyManagers = kKeyManagerFactory.getKeyManagers();

        //        final SSLSocketFactory factory = sslc.getSocketFactory();
                for (int i = 0; i < keyManagers.length; i++) {
                    if (keyManagers[i] instanceof X509KeyManager) {
                        keyManagers[i] = new GUIKeyManager((X509KeyManager) keyManagers[i]);
                    }
                }

                // Now construct a SSLContext using these (possibly wrapped)
                // KeyManagers, and the TrustManagers. We still use a null
                // SecureRandom, indicating that the defaults should be used.
                SSLContext context = SSLContext.getInstance("TLS");
                
                if (LOG.isDebugEnabled()) {
                    StringBuilder buff = new StringBuilder();
                    buff.append("Available providers: \n");
                    for (Provider p : Security.getProviders()) {
                       buff.append(p).append("\n");
                    }
                    LOG.debug(buff.toString());
                }
                
                context.init(keyManagers, tTrustManagerFactory.getTrustManagers(), new SecureRandom());

                // Finally, we get a SocketFactory, and pass it to SimpleSSLClient.
                SSLSocketFactory factory = context.getSocketFactory();

                HttpsURLConnection.setDefaultSSLSocketFactory(factory);
                
                QName qname = new QName("http://ws.protocol.core.ejbca.org/", "EjbcaWSService");
                  EjbcaWSService service = new EjbcaWSService(new URL(urlstr),qname);
                  ejbcaWS = service.getEjbcaWSPort();
                  if (factory != null) {
                      final BindingProvider bp = (BindingProvider) ejbcaWS;
                      final Client client = ClientProxy.getClient(bp);
                      final HTTPConduit http = (HTTPConduit) client.getConduit();
                      final TLSClientParameters params = new TLSClientParameters();

                      params.setSSLSocketFactory(factory);
                      http.setTlsClientParameters(params);
                  }
            dispose();
        } catch (Exception ex) {
            LOG.error("Connection failed", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Connect", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_connectButtonActionPerformed

    private void truststoreBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//NOPMD//GEN-FIRST:event_truststoreBrowseButtonActionPerformed
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(truststoreFilePathTextField.getText()));
        final int result  = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            truststoreFilePathTextField.setText(
                    chooser.getSelectedFile().getAbsolutePath());
        }
    }//GEN-LAST:event_truststoreBrowseButtonActionPerformed

    private void keystoreBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//NOPMD//GEN-FIRST:event_keystoreBrowseButtonActionPerformed
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(keystoreFilePathTextField.getText()));
        final int result  = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            keystoreFilePathTextField.setText(
                    chooser.getSelectedFile().getAbsolutePath());
        }
    }//GEN-LAST:event_keystoreBrowseButtonActionPerformed

    private void defaultsButtonActionPerformed(java.awt.event.ActionEvent evt) {//NOPMD//GEN-FIRST:event_defaultsButtonActionPerformed
        loadSettingsFromFile(DEFAULT_CONNECT_FILE);
    }//GEN-LAST:event_defaultsButtonActionPerformed

    private void truststoreTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//NOPMD//GEN-FIRST:event_truststoreTypeComboBoxActionPerformed
        final String type = (String) truststoreTypeComboBox.getSelectedItem();
        truststorePasswordField.setEnabled(!TRUSTSTORE_TYPE_PEM.equals(type)
                && !TRUSTSTORE_TYPE_KEYSTORE.equals(type));
        truststorePasswordLabel.setEnabled(!TRUSTSTORE_TYPE_PEM.equals(type)
                && !TRUSTSTORE_TYPE_KEYSTORE.equals(type));
        truststoreFilePathLabel.setEnabled(
                !TRUSTSTORE_TYPE_KEYSTORE.equals(type));
        truststoreFilePathTextField.setEnabled(
                !TRUSTSTORE_TYPE_KEYSTORE.equals(type));
        truststoreBrowseButton.setEnabled(
                !TRUSTSTORE_TYPE_KEYSTORE.equals(type));
    }//GEN-LAST:event_truststoreTypeComboBoxActionPerformed

    private void loadSettingsFromFile(final File file) {
        try {
            final Properties defaults = new Properties();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Trying to load from file " + file.getAbsolutePath());
            }
            defaults.load(new FileInputStream(file));

            ConnectSettings sett = new ConnectSettings();
            sett.setUrl(defaults.getProperty("url", DEFAULT_URL));
            sett.setTruststoreType(defaults.getProperty("truststoreType"));
            sett.setTruststoreFile(defaults.getProperty("truststoreFile"));
            if (defaults.getProperty("truststorePassword") != null) {
                sett.setTruststorePassword(defaults.getProperty("truststorePassword").toCharArray());
            }
            sett.setKeystoreType(defaults.getProperty("keystoreType"));
            sett.setKeystoreFile(defaults.getProperty("keystoreFile"));

            loadSettings(sett);
        } catch (IOException ex) {
            LOG.error("Load settings failed", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Reset defaults", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSettings(ConnectSettings settings) {
        urlTextField.setText(settings.getUrl());
        truststoreTypeComboBox.setSelectedItem(settings.getTruststoreType());
        truststoreFilePathTextField.setText(settings.getTruststoreFile());
        if (settings.getTruststorePassword() != null) {
            truststorePasswordField.setText(new String(settings.getTruststorePassword())); // TODO
        }
        keystoreTypeComboBox.setSelectedItem(settings.getKeystoreType());
        keystoreFilePathTextField.setText(settings.getKeystoreFile());
//        if (settings.getKeystorePassword() != null) {
//            keystorePasswordField.setText(new String(settings.getKeystorePassword())); // TODO
//        }
    }

    public ConnectSettings getSettings() {
        return settings;
    }

    private static Provider getPKCS11ProviderUsingConfigMethod(final Method configMethod,
            final String config)
                    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        final Provider prototype = Security.getProvider("SunPKCS11");
        final Provider provider = (Provider) configMethod.invoke(prototype, config);

        return provider;
    }

    private static String getSunP11ConfigStringFromInputStream(final InputStream is) throws IOException {
        final StringBuilder configBuilder = new StringBuilder();
        /* we need to prepend -- to indicate to the configure() method
         * that the config is treated as a string
         */
        configBuilder.append("--").append(IOUtils.toString(is, StandardCharsets.UTF_8));
        return configBuilder.toString();
    }

    
    private static KeyStore getLoadedKeystorePKCS11(final String name, final String library, final char[] authCode, KeyStore.CallbackHandlerProtection callbackHandlerProtection) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        final KeyStore keystore;

        final InputStream config = new ByteArrayInputStream(
                new StringBuilder().append("name=").append(name).append("\n")
                        .append("library=").append(library).append("\n")
                        .append("showInfo=true")
                        .toString().getBytes());

            try {
                    final Class<?> klass = Class.forName("sun.security.pkcs11.SunPKCS11");
                    Provider provider;
                    
                    try {
                        /* try getting the Java 9+ configure method first
                         * if this fails, fall back to the old way, calling the
                         * constructor
                         */
                        final Class<?>[] paramString = new Class[1];    
                        paramString[0] = String.class;
                        final Method method =
                                Provider.class.getDeclaredMethod("configure",
                                                                 paramString);
                        final String configString =
                                getSunP11ConfigStringFromInputStream(config);
                        
                        provider = getPKCS11ProviderUsingConfigMethod(method, configString);
                    } catch (NoSuchMethodException e) {
                        // find constructor taking one argument of type InputStream
                        Class<?>[] parTypes = new Class[1];
                        parTypes[0] = InputStream.class;

                        Constructor<?> ctor = klass.getConstructor(parTypes);           
                        Object[] argList = new Object[1];
                        argList[0] = config;
                        provider = (Provider) ctor.newInstance(argList);
                    }

                    Security.addProvider(provider);

                    final KeyStore.Builder builder = KeyStore.Builder.newInstance("PKCS11",
                            provider, callbackHandlerProtection);

                    keystore = builder.getKeyStore();
                    keystore.load(null, authCode);

                    final Enumeration<String> e = keystore.aliases();
                    while( e.hasMoreElements() ) {
                        final String keyAlias = e.nextElement();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("******* keyAlias: " + keyAlias
                                    + ", certificate: "
                                    + ((X509Certificate) keystore.getCertificate(keyAlias))
                                        .getSubjectDN().getName());
                        }
                    }
            } catch (NoSuchMethodException nsme) {
                throw new KeyStoreException("Could not find constructor for keystore provider", nsme);
            } catch (InstantiationException ie) {
                throw new KeyStoreException("Failed to instantiate keystore provider", ie);
            } catch (ClassNotFoundException ncdfe) {
                throw new KeyStoreException("Unsupported keystore provider", ncdfe);
            } catch (InvocationTargetException ite) {
                throw new KeyStoreException("Could not initialize provider", ite);
            } catch (Exception e) {
                throw new KeyStoreException("Error", e);
            }
        return keystore;
    }

    private static KeyStore getLoadedKeystoreCSP(final String storeType, KeyStore.CallbackHandlerProtection callbackHandlerProtection) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        final KeyStore keystore;

        final KeyStore.Builder builder = KeyStore.Builder.newInstance(storeType,
                null, callbackHandlerProtection);

        keystore = builder.getKeyStore();
        keystore.load(null, null);

        final Enumeration<String> e = keystore.aliases();
        while( e.hasMoreElements() ) {
            final String keyAlias = e.nextElement();
            if (LOG.isDebugEnabled()) {
                LOG.debug("******* keyAlias: " + keyAlias
                        + ", certificate: "
                    + keystore.getCertificate(keyAlias));
            }

        }
        return keystore;
    }

    private KeyStore getLoadedKeystore(final String fileName, final char[] authcode, final String storeType,
            final String provider) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, NoSuchProviderException {
        
        final KeyStore keystore;
        if (provider == null) {
            keystore = KeyStore.getInstance(storeType);
        } else {
            keystore = KeyStore.getInstance(storeType, provider);
        }

        InputStream in = null;
        try {
            if (fileName != null && !fileName.isEmpty()) {
                in = new FileInputStream(fileName);
            }
            keystore.load(in, authcode);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {} // NOPMD
            }
        }

        return keystore;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton cancelButton;
    private JButton connectButton;
    private JButton defaultsButton;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JLabel jLabel8;
    private JLabel jLabel9;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JPanel jPanel4;
    private JButton keystoreBrowseButton;
    private JTextField keystoreFilePathTextField;
    private JComboBox<String> keystoreTypeComboBox;
    private JPasswordField passwordField;
    private JLabel passwordLabel;
    private JPanel passwordPanel;
    private JButton truststoreBrowseButton;
    private JLabel truststoreFilePathLabel;
    private JTextField truststoreFilePathTextField;
    private JPasswordField truststorePasswordField;
    private JLabel truststorePasswordLabel;
    private JComboBox<String> truststoreTypeComboBox;
    private JTextField urlTextField;
    // End of variables declaration//GEN-END:variables

    public EjbcaWS getEjbcaWS() {
        return ejbcaWS;
    }

}
