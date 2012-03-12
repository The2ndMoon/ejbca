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
package org.cesecore.certificates.ocsp.cache;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.util.CertTools;

/**
 * This is a tuple object to wrap a crypto token and a X509Certificate chain. 
 * 
 * @version $Id$
 *
 */
public class CryptoTokenAndChain implements Serializable {

    private static final long serialVersionUID = 1269603699779500195L;
    private CryptoToken cryptoToken;
    private X509Certificate[] chain;
    private String privateKeyAlias;
    
    private final int caCertPosition;
    
    public CryptoTokenAndChain(CryptoToken cryptoToken, X509Certificate[] chain, String privateKeyAlias) {
        this.cryptoToken = cryptoToken;
        this.chain = chain;    
        this.privateKeyAlias = privateKeyAlias;
        
        if(CertTools.isCA(chain[0])) {
            //A CA or SUBCA
            caCertPosition = 0;
        } else {
            //OCSP certificate in position 0
            caCertPosition =1;
        }
    }

    /**
     * 
     * @return the private key from this crypto token.
     * @throws CryptoTokenOfflineException if Crypto Token is not available or connected, or key with alias does not exist.
     */
    public PrivateKey getPrivateKey() throws CryptoTokenOfflineException {
        return cryptoToken.getPrivateKey(privateKeyAlias);
    }

    /**
     * 
     * @return the signer provider name
     */
    public String getSignProviderName() {
        return cryptoToken.getSignProviderName();
    }
    
    /**
     * @return the chain
     */
    public X509Certificate[] getChain() {
        return chain;
    }
    
    public X509Certificate getCaCertificate() {
       return chain[caCertPosition];
    }


}
