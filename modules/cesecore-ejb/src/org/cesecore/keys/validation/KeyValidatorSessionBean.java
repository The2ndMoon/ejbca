/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General                  *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.cesecore.keys.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.cesecore.audit.enums.EventStatus;
import org.cesecore.audit.enums.EventTypes;
import org.cesecore.audit.enums.ModuleTypes;
import org.cesecore.audit.enums.ServiceTypes;
import org.cesecore.audit.log.SecurityEventsLoggerSessionLocal;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.AuthorizationSessionLocal;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.certificates.ca.CA;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.ca.IllegalValidityException;
import org.cesecore.certificates.ca.internal.CertificateValidity;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionLocal;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.internal.InternalResources;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.profiles.ProfileBase;
import org.cesecore.profiles.ProfileData;
import org.cesecore.profiles.ProfileSessionLocal;
import org.cesecore.util.SecureXMLDecoder;

/**
 * Handles management of key validators.
 * 
 * @version $Id$
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "KeyValidatorSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class KeyValidatorSessionBean implements KeyValidatorSessionLocal, KeyValidatorSessionRemote {

    /** Class logger. */
    private static final Logger log = Logger.getLogger(KeyValidatorSessionBean.class);

    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

    @EJB
    private AuthorizationSessionLocal authorizationSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private CertificateProfileSessionLocal certificateProfileSession;
    @EJB
    private ProfileSessionLocal profileSession;
    @EJB
    private SecurityEventsLoggerSessionLocal auditSession;



    @Override
    public Validator getValidator(int id) {
        return getKeyValidatorInternal(id, true);
    }

    @Override
    public String getKeyValidatorName(int id) {
        if (log.isTraceEnabled()) {
            log.trace(">getKeyValidatorName(id: " + id + ")");
        }
        final Validator entity = getKeyValidatorInternal(id, true);
        String result = null;
        if (null != entity) {
            result = entity.getProfileName();
        }
        if (log.isTraceEnabled()) {
            log.trace("<getKeyValidatorName(): " + result);
        }
        return result;
    }

    @Override
    public void importValidator(AuthenticationToken admin, Validator validator)
            throws AuthorizationDeniedException, KeyValidatorExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">addKeyValidator(name: " + validator.getProfileName() + ", id: " + validator.getProfileId() + ")");
        }
        addKeyValidatorInternal(admin, validator);
        final String message = intres.getLocalizedMessage("keyvalidator.addedkeyvalidator", validator.getProfileName());
        final Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("msg", message);
        auditSession.log(EventTypes.VALIDATOR_CREATION, EventStatus.SUCCESS, ModuleTypes.VALIDATOR, ServiceTypes.CORE, admin.toString(), null,
                null, null, details);
        if (log.isTraceEnabled()) {
            log.trace("<addKeyValidator()");
        }
    }
    

    @Override
    public ValidatorImportResult importKeyValidatorsFromZip(final AuthenticationToken authenticationToken, final byte[] filebuffer)
            throws AuthorizationDeniedException, ZipException {
        List<Validator> importedValidators = new ArrayList<>();
        List<String> ignoredValidators = new ArrayList<>();
        if (filebuffer.length == 0) {
            throw new IllegalArgumentException("No input file");
        }
        final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(filebuffer));
        try {
            ZipEntry ze = zis.getNextEntry();
            if (ze == null) {
                throw new ZipException("Was expecting a zip file.");
            }

            do {
                String filename = ze.getName();
                if (log.isDebugEnabled()) {
                    log.debug("Importing file: " + filename);
                }
                if (ignoreFile(filename)) {
                    ignoredValidators.add(filename);
                    continue;
                }
                try {
                    filename = URLDecoder.decode(filename, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("UTF-8 was not a known character encoding", e);
                }
                int index1 = filename.indexOf("_");
                int index2 = filename.lastIndexOf("-");
                int index3 = filename.lastIndexOf(".xml");
                String nameToImport = filename.substring(index1 + 1, index2);
                int idToImport = 0;
                try {
                    idToImport = Integer.parseInt(filename.substring(index2 + 1, index3));
                } catch (NumberFormatException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("NumberFormatException parsing key validator id: " + e.getMessage());
                    }
                    ignoredValidators.add(filename);
                    continue;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Extracted key validator name '" + nameToImport + "' and ID '" + idToImport + "'");
                }
                if (ignoreKeyValidator(filename, idToImport)) {
                    ignoredValidators.add(filename);
                    continue;
                }
                if (getValidator(idToImport) != null) {
                    log.warn("Key valildator id '" + idToImport + "' already exist in database. Adding with a new key validator id instead.");
                    idToImport = -1; // means we should create a new id when adding the key validator.
                }
                final byte[] filebytes = new byte[102400];
                int i = 0;
                while ((zis.available() == 1) && (i < filebytes.length)) {
                    filebytes[i++] = (byte) zis.read();
                }
                final Validator validator = getKeyValidatorFromByteArray(nameToImport, filebytes);
                if (validator == null) {
                    ignoredValidators.add(filename);
                    log.info("Ignoring validator " + filename);
                    continue;
                }
                try {
                    if (idToImport == -1) {
                        int validatorId =  addKeyValidator(authenticationToken, validator);
                        validator.setProfileId(validatorId);
                    } else {
                        if (getValidator(idToImport) == null) {
                            importValidator(authenticationToken, validator);
                        } else {
                            log.info("Ignoring validator " + validator.getProfileName() + " as it already exists.");
                            ignoredValidators.add(validator.getProfileName());
                        }
                    }
                } catch (KeyValidatorExistsException e) {
                    throw new IllegalStateException("Key validator already exists in spite of the fact that we've just checked that it doesn't.", e);
                }
                importedValidators.add(validator);
                log.info("Added key validator: " + nameToImport);
            } while ((ze = zis.getNextEntry()) != null);
            zis.closeEntry();
            zis.close();
        } catch (ZipException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException caught.", e);
        }
        return new ValidatorImportResult(importedValidators, ignoredValidators);

    }

    /**
     * Gets a key validator by the XML file stored in the byte[].    
     * @param name the name of the key validator
     * @param bytes the XML file as bytes
     * @return the concrete key validator implementation.
     * @throws AuthorizationDeniedException if not authorized
     */
    @SuppressWarnings("unchecked")
    private Validator getKeyValidatorFromByteArray(final String name, final byte[] bytes) throws AuthorizationDeniedException {
        final ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        Validator validator = null;
        try {
            final SecureXMLDecoder decoder = new SecureXMLDecoder(is);
            LinkedHashMap<Object, Object> data = null;
            try {
                data = (LinkedHashMap<Object, Object>) decoder.readObject();
                validator = ((Class<? extends Validator>) data.get(ProfileBase.PROFILE_TYPE)).newInstance();
                validator.setDataMap(data);
            } catch (IOException|InstantiationException | IllegalAccessException e) {
                log.info("Error parsing keyvalidator data: " + e.getMessage());
                if (log.isDebugEnabled()) {
                    log.debug("Full stack trace: ", e);
                }
                return null;
            } finally {
                decoder.close();
            }

            // Make sure certificate profiles exists.
            final List<Integer> certificateProfileIds = validator.getCertificateProfileIds();
            final ArrayList<Integer> certificateProfilesToRemove = new ArrayList<Integer>();
            for (Integer certificateProfileId : certificateProfileIds) {
                if (null == certificateProfileSession.getCertificateProfile(certificateProfileId)) {
                    certificateProfilesToRemove.add(certificateProfileId);
                }
            }
            for (Integer toRemove : certificateProfilesToRemove) {
                log.warn("Warning: certificate profile with id " + toRemove + " was not found and will not be used in key validator '" + name + "'.");
                certificateProfileIds.remove(toRemove);
            }
            if (certificateProfileIds.size() == 0) {
                log.warn("Warning: No certificate profiles left in key validator '" + name + "'.");
                certificateProfileIds.add(Integer.valueOf(CertificateProfile.ANYCA));
            }
            validator.setCertificateProfileIds(certificateProfileIds);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new IllegalStateException("Unknown IOException was caught when closing stream", e);
            }
        }
        return validator;
    }

    /** 
     * Check ignore file.
     * @return true if the file shall be ignored from a key validator import, false if it should be imported. 
     */
    private boolean ignoreFile(final String filename) {
        if (filename.lastIndexOf(".xml") != (filename.length() - 4)) {
            log.info(filename + " is not an XML file. IGNORED");
            return true;
        }

        if (filename.indexOf("_") < 0 || filename.lastIndexOf("-") < 0 || (filename.indexOf("keyvalidator_") < 0)) {
            log.info(filename + " is not in the expected format. " + "The file name should look like: keyvalidator_<name>-<id>.xml. IGNORED");
            return true;
        }
        return false;
    }

    /** 
     * Check ignore key validator.
     * @return true if the key validator should be ignored from a import because it already exists, false if it should be imported. 
     */
    private boolean ignoreKeyValidator(final String filename, final int id) {
        if (getValidator(id) != null) {
            log.info("Key validator with ID'" + id + "' already exist in database. IGNORED");
            return true;
        }
        return false;
    }
    
   

    @Override
    public void changeKeyValidator(AuthenticationToken admin, Validator validator)
            throws AuthorizationDeniedException, KeyValidatorDoesntExistsException {
        assertIsAuthorizedToEditValidators(admin);
        ProfileData data = profileSession.findById(validator.getProfileId());   
        final String message;
        final String name = validator.getProfileName();
        if (data != null) {
            profileSession.changeProfile(validator);
            // Since loading a KeyValidator is quite complex, we simple purge the cache here.
            ValidatorCache.INSTANCE.removeEntry(data.getId());
            message = intres.getLocalizedMessage("keyvalidator.changedkeyvalidator", name);
            final Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("msg", message);
            //TODO: Include a diff in the changelog (profileData.getProfile().diff(profile);), but make sure to resolve all steps so that we don't
            //      output a ton of serialized garbage (see ECA-5276)
            auditSession.log(EventTypes.VALIDATOR_CHANGE, EventStatus.SUCCESS, ModuleTypes.VALIDATOR, ServiceTypes.CORE, admin.toString(),
                    null, null, null, details);
        } else {
            message = intres.getLocalizedMessage("keyvalidator.errorchangekeyvalidator", name);
            log.info(message);
            throw new KeyValidatorDoesntExistsException("Validator by ID " + validator.getProfileId() + " does not exist in database.");
        }
    }

    @Override
    public void removeKeyValidator(AuthenticationToken admin, final int validatorId)
            throws AuthorizationDeniedException, KeyValidatorDoesntExistsException, CouldNotRemoveKeyValidatorException {
        if (log.isTraceEnabled()) {
            log.trace(">removeKeyValidator(id: " + validatorId + ")");
        }
        assertIsAuthorizedToEditValidators(admin);
        String message;
       
            ProfileData data = profileSession.findById(validatorId);
            if (data == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Trying to remove a key validator that does not exist with ID: " + validatorId);
                }
                throw new KeyValidatorDoesntExistsException();
            } else {
                if (caSession.existsKeyValidatorInCAs(data.getId())) {
                    throw new CouldNotRemoveKeyValidatorException();
                }
                profileSession.removeProfile(data);
                // Purge the cache here.
                ValidatorCache.INSTANCE.removeEntry(data.getId());
                message = intres.getLocalizedMessage("keyvalidator.removedkeyvalidator", data.getProfileName());
                final Map<String, Object> details = new LinkedHashMap<String, Object>();
                details.put("msg", message);
                auditSession.log(EventTypes.VALIDATOR_REMOVAL, EventStatus.SUCCESS, ModuleTypes.VALIDATOR, ServiceTypes.CORE, admin.toString(),
                        null, null, null, details);
            }
   
        if (log.isTraceEnabled()) {
            log.trace("<removeKeyValidator()");
        }
    }

    @Override
    public void flushKeyValidatorCache() {
        ValidatorCache.INSTANCE.flush();
        if (log.isDebugEnabled()) {
            log.debug("Flushed KeyValidator cache.");
        }
    }

    @Override
    public int addKeyValidator(AuthenticationToken admin, Validator validator)
            throws AuthorizationDeniedException, KeyValidatorExistsException {
        return addKeyValidatorInternal(admin, validator);
    }

    @Override
    public void cloneKeyValidator(final AuthenticationToken admin, final int validatorId, final String newName)
            throws  AuthorizationDeniedException, KeyValidatorDoesntExistsException, KeyValidatorExistsException {
        cloneKeyValidator(admin, getKeyValidatorInternal(validatorId, true), newName);
    }
    
    @Override
    public void cloneKeyValidator(final AuthenticationToken admin, final Validator validator, final String newName)
            throws  AuthorizationDeniedException, KeyValidatorDoesntExistsException, KeyValidatorExistsException {
        Validator validatorClone = null;
        final Integer origProfileId = validator.getProfileId();
        if (origProfileId == null) {
            throw new KeyValidatorDoesntExistsException("Could not find key validator " + validator.getProfileName());
        }
        validatorClone = getValidator(origProfileId).clone();
        validatorClone.setProfileName(newName);
        try {
            addKeyValidatorInternal(admin, validatorClone);
            final String message = intres.getLocalizedMessage("keyvalidator.clonedkeyvalidator", newName, validator.getProfileName());
            final Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("msg", message);
            auditSession.log(EventTypes.VALIDATOR_CREATION, EventStatus.SUCCESS, ModuleTypes.VALIDATOR, ServiceTypes.CORE, admin.toString(),
                    null, null, null, details);
        } catch (KeyValidatorExistsException e) {
            final String message = intres.getLocalizedMessage("keyvalidator.errorclonekeyvalidator", newName, validator.getProfileName());
            log.info(message);
            throw e;
        }   
    }
    
    @Override
    public void renameKeyValidator(AuthenticationToken admin, final int validatorId, String newName)
            throws AuthorizationDeniedException, KeyValidatorDoesntExistsException, KeyValidatorExistsException {
        renameKeyValidator(admin, getKeyValidatorInternal(validatorId, true), newName);
    }

    @Override
    public void renameKeyValidator(AuthenticationToken admin, final Validator validator, String newName)
            throws AuthorizationDeniedException, KeyValidatorDoesntExistsException, KeyValidatorExistsException {
        if (log.isTraceEnabled()) {
            log.trace(">renameKeyValidator(from " + validator.getProfileName() + " to " + newName + ")");
        }
        assertIsAuthorizedToEditValidators(admin);
        boolean success = false;
        if (profileSession.findByNameAndType(newName, Validator.TYPE_NAME).isEmpty()) {
            ProfileData data = profileSession.findById(validator.getProfileId());
            if (data != null) {
                data.setProfileName(newName);
                success = true;
                // Since loading a key validator is quite complex, we simple purge the cache here.
                ValidatorCache.INSTANCE.removeEntry(data.getId());
            }
        }
        if (success) {
            final String message = intres.getLocalizedMessage("keyvalidator.renamedkeyvalidator", validator.getProfileName(), newName);
            final Map<String, Object> details = new LinkedHashMap<String, Object>();
            details.put("msg", message);
            auditSession.log(EventTypes.VALIDATOR_RENAME, EventStatus.SUCCESS, ModuleTypes.VALIDATOR, ServiceTypes.CORE, admin.toString(),
                    null, null, null, details);
        } else {
            final String message = intres.getLocalizedMessage("keyvalidator.errorrenamekeyvalidator", validator.getProfileName(), newName);
            log.info(message);
            throw new KeyValidatorExistsException();
        }
        if (log.isTraceEnabled()) {
            log.trace("<renameKeyValidator()");
        }
    }

    @Override
    public Map<Integer, Validator> getAllKeyValidators() {
        final List<ProfileData> keyValidators = profileSession.findAllProfiles(Validator.TYPE_NAME);
        final Map<Integer, Validator> result = new HashMap<>();
        for (ProfileData data : keyValidators) {
            //Cast is safe since we know we retrieved the correct implementation
            result.put(data.getId(), (Validator) data.getProfile());
        }
        if (log.isDebugEnabled()) {
            for (Integer id: result.keySet()) {
                log.debug("Key validators found in datastore: " + id+":"+result.get(id).getProfileName());                
            }
        }
        return result;
    }

    @Override
    public Map<Integer, Validator> getKeyValidatorsById(Collection<Integer> ids) {
        final List<ProfileData> keyValidators = profileSession.findAllProfiles(Validator.TYPE_NAME);
        final Map<Integer, Validator> result = new HashMap<>();
        for (ProfileData data : keyValidators) {
            result.put(data.getId(), (Validator) data.getProfile());
        }
        if (log.isDebugEnabled()) {
            for (Integer id: result.keySet()) {
                log.debug("Key validators found in datastore: " + id+":"+result.get(id).getProfileName());                
            }
        }
        return result;
    }

    @Override
    public Map<Integer, String> getKeyValidatorIdToNameMap() {
        final HashMap<Integer, String> result = new HashMap<>();
        for (ProfileData data : profileSession.findAllProfiles(Validator.TYPE_NAME)) {
            result.put(data.getId(), data.getProfileName());
        }
        return result;
    }

    @Override
    public boolean validatePublicKey(final CA ca, EndEntityInformation endEntityInformation, CertificateProfile certificateProfile, Date notBefore,
            Date notAfter, PublicKey publicKey) throws KeyValidationException, IllegalValidityException {
        boolean result = true;
        // ECA-4219 Workaround: While CA creation, select key validators in AdminGUI -> Edit CAs -> Create CA -> Key Validators.
        // ca != null because of import or update of external certificates.
        if (ca != null && !CollectionUtils.isEmpty(ca.getValidators())) { // || certificateProfile.isTypeRootCA() || certificateProfile.isTypeSubCA()
            final CertificateValidity certificateValidity = new CertificateValidity(endEntityInformation, certificateProfile, notBefore, notAfter,
                    ca.getCACertificate(), false);
            if (log.isDebugEnabled()) {
                log.debug("Validate " + publicKey.getAlgorithm() + " public key with " + publicKey.getFormat() + " format.");
                log.debug("Certificate 'notBefore' " + certificateValidity.getNotBefore());
                log.debug("Certificate 'notAfter' " + certificateValidity.getNotAfter());
            }
            final Map<Integer, Validator> map = getKeyValidatorsById(ca.getValidators());
            final List<Integer> ids = new ArrayList<>(map.keySet());
            Validator keyValidator;
            String name = null;
            for (Integer id : ids) {
                keyValidator = map.get(id);
                keyValidator.setCertificateProfile(certificateProfile);
                name = keyValidator.getProfileName();
                if (log.isTraceEnabled()) {
                    log.trace("Try to apply key validator: " + keyValidator.toDisplayString());
                }
                try {
                    // Filter for base key validator critieria.
                    final List<Integer> certificateProfileIds = keyValidator.getCertificateProfileIds();
                    if (null != certificateProfileIds && !certificateProfileIds.contains(endEntityInformation.getCertificateProfileId())) {
                        if (log.isDebugEnabled()) {
                            log.debug(intres.getLocalizedMessage("keyvalidator.filterconditiondoesnotmatch", name, "applicableCertificateProfiles"));
                        }
                        continue;
                    }
                    if (!KeyValidatorDateConditions.evaluate(keyValidator.getNotBefore(), certificateValidity.getNotBefore(),
                            keyValidator.getNotBeforeCondition())) {
                        if (log.isDebugEnabled()) {
                            log.debug(intres.getLocalizedMessage("keyvalidator.filterconditiondoesnotmatch", name, "notBefore"));
                        }
                        continue;
                    }
                    if (!KeyValidatorDateConditions.evaluate(keyValidator.getNotAfter(), certificateValidity.getNotAfter(),
                            keyValidator.getNotAfterCondition())) {
                        if (log.isDebugEnabled()) {
                            log.debug(intres.getLocalizedMessage("keyvalidator.filterconditiondoesnotmatch", name, "notAfter"));
                        }
                        continue;
                    }
                    log.info(intres.getLocalizedMessage("keyvalidator.isbeingprocessed", name, endEntityInformation.getUsername()));
                    keyValidator.before();
                    if (!(result = keyValidator.validate(publicKey))) {
                        postProcessKeyValidation(keyValidator, result);
                    }
                } catch (KeyValidationException e) {
                    throw e;
                } finally {
                    keyValidator.after();
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("No key validator configured for CA " + ca.getName() + " (ID=" + ca.getCAId() + ").");
            }
        }
        return result;
    }

    /**
     * Post processes a key validator by the result of its validation and the failedAction stored in the BaseKeyValidator.
     * @param keyValidator the key validator.
     * @param result the evaulation result.
     */
    private void postProcessKeyValidation(Validator keyValidator, boolean result) throws KeyValidationException {
        final String name = keyValidator.getProfileName();
        if (!result) { // Evaluation has failed.
            final int index = keyValidator.getFailedAction();
            final String message = intres.getLocalizedMessage("keyvalidator.validationfailed", name, keyValidator.getMessages());
            if (KeyValidationFailedActions.LOG_INFO.getIndex() == index) {
                log.info(message);
            } else if (KeyValidationFailedActions.LOG_WARN.getIndex() == index) {
                log.warn(message);
            } else if (KeyValidationFailedActions.LOG_ERROR.getIndex() == index) {
                log.error(message);
            } else if (KeyValidationFailedActions.ABORT_CERTIFICATE_ISSUANCE.getIndex() == index) {
                if (log.isDebugEnabled()) {
                    log.debug("Action ABORT_CERTIFICATE_ISSUANCE: "+ message);                    
                }
                throw new KeyValidationException(message);
            } else {
                // NOOP
                log.debug(message);
            }
        } else {
            final String message = intres.getLocalizedMessage("keyvalidator.validationsuccessful", name, keyValidator.getPublicKey().getEncoded());
            log.info(message);
        }
    }

    public boolean authorizedToKeyValidatorWithResource(AuthenticationToken admin, CertificateProfile profile, boolean logging, String... resources) {
        // We need to check that admin also have rights to the passed in resources
        final List<String> rules = new ArrayList<>(Arrays.asList(resources));
        // Check that admin is authorized to all CAids
        for (final Integer caid : profile.getAvailableCAs()) {
            rules.add(StandardRules.CAACCESS.resource() + caid);
        }
        // Perform authorization check
        boolean ret = false;
        if (logging) {
            ret = authorizationSession.isAuthorized(admin, rules.toArray(new String[rules.size()]));
        } else {
            ret = authorizationSession.isAuthorizedNoLogging(admin, rules.toArray(new String[rules.size()]));
        }
        return ret;
    }

    @Override
    public Collection<Integer> getAuthorizedKeyValidatorIds(AuthenticationToken admin, String keyValidatorAccessRule) {
        final ArrayList<Integer> result = new ArrayList<Integer>();
        final Map<Integer, String> map = getKeyValidatorIdToNameMap();
        String accessRule;
        boolean authorized;
        if (authorizationSession.isAuthorizedNoLogging(admin, StandardRules.ROLE_ROOT.resource())) {
            for (final Entry<Integer, String> entry : map.entrySet()) {
                // ECA-4219 Fix. Authorization does not seem to be effective. If so, it would NOT be put into list for AdminGUI -> Amdin. Privileges -> Access Rules -> Base Mode -> Key Validators. But it would still appear in the Advanced Mode!
                // accessRule = "/keyvalidator/" + entry.getValue() + keyValidatorAccessRule; // AccessRulesConstants.KEYVALIDATORPREFIX not available here.
                accessRule = "/keyvalidatorrules/" + entry.getValue().toString() + keyValidatorAccessRule;
                authorized = authorizationSession.isAuthorizedNoLogging(admin, accessRule);
                if (log.isDebugEnabled()) {
                    log.debug("Access rule " + accessRule + " authorized " + authorized);
                }
                if (authorized) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    /** Adds a key validator or throws an exception. 
     * @return the profile ID
     */
    private int addKeyValidatorInternal(AuthenticationToken admin, Validator keyValidator)
            throws AuthorizationDeniedException, KeyValidatorExistsException {
        assertIsAuthorizedToEditValidators(admin);
        if (profileSession.findByNameAndType(keyValidator.getProfileName(), Validator.TYPE_NAME).isEmpty()) {
            return profileSession.addProfile(keyValidator);
        } else {
            final String message = intres.getLocalizedMessage("keyvalidator.erroraddkeyvalidator", keyValidator.getProfileName());
            log.info(message);
            throw new KeyValidatorExistsException();
        }
    }

    /** Gets a key validator by cache or database, can return null. Puts it into the cache, if not already present. */
    private Validator getKeyValidatorInternal(int id, boolean fromCache) {
        Validator result = null;
        // If we should read from cache, and we have an id to use in the cache, and the cache does not need to be updated
        if (fromCache && !ValidatorCache.INSTANCE.shouldCheckForUpdates(id)) {
            // Get from cache (or null)
            result = ValidatorCache.INSTANCE.getEntry(id);
        }

        // if we selected to not read from cache, or if the cache did not contain this entry
        if (result == null) {

            // We need to read from database because we specified to not get from cache or we don't have anything in the cache
            final ProfileData data = profileSession.findById(id);
            
            if (data != null) {
                result = (Validator) data.getProfile();
                final int digest = data.getProtectString(0).hashCode();
                // The cache compares the database data with what is in the cache
                // If database is different from cache, replace it in the cache
                ValidatorCache.INSTANCE.updateWith(data.getId(), digest, data.getProfileName(), result);
            } else {
                // Ensure that it is removed from cache if it exists
                ValidatorCache.INSTANCE.removeEntry(id);
            }
        }

        return result;
    }

    /** Assert the administrator is authorized to edit key validators. */
    private void assertIsAuthorizedToEditValidators(AuthenticationToken admin) throws AuthorizationDeniedException {
        if (!authorizationSession.isAuthorized(admin, StandardRules.VALIDATOREDIT.resource())) {
            final String message = intres.getLocalizedMessage("store.editkeyvalidatornotauthorized", admin.toString());
            throw new AuthorizationDeniedException(message);
        }
    }
}