--
-- These definitions should work for EJBCA 3.8.x, PostgreSQL 8.1 or 8.2.
-- These definitions are made and tested for Websphere.
--

DROP TABLE ACCESSRULESDATA;

CREATE TABLE accessrulesdata (
  pK INT4 NOT NULL, 
  accessRule TEXT, 
  rule INT4 NOT NULL, 
  isRecursive BOOLEAN DEFAULT FALSE NOT NULL, 
  AdminGroupData_accessRules INT4, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_accessrulesdata PRIMARY KEY (pK)
);

DROP TABLE ADMINENTITYDATA;
  
CREATE TABLE adminentitydata (
  pK INT4 NOT NULL, 
  matchWith INT4 NOT NULL, 
  matchType INT4 NOT NULL, 
  matchValue TEXT, 
  AdminGroupData_adminEntities INT4, 
  cAId INT4 NOT NULL, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_adminentitydata PRIMARY KEY (pK)
);
   
DROP TABLE ADMINGROUPDATA;

CREATE TABLE admingroupdata (
  pK INT4 NOT NULL, 
  adminGroupName TEXT, 
  cAId INT4 NOT NULL, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_admingroupdata PRIMARY KEY (pK)
);

DROP TABLE ADMINPREFERENCESDATA;

CREATE TABLE adminpreferencesdata (
  id TEXT NOT NULL, 
  data BYTEA, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_adminpreferencesdata PRIMARY KEY (id)
);

DROP TABLE APPROVALDATA;

CREATE TABLE approvaldata (
  id INT4 NOT NULL, 
  approvalid INT4 NOT NULL, 
  approvaltype INT4 NOT NULL, 
  endentityprofileid INT4 NOT NULL, 
  caid INT4 NOT NULL, 
  reqadmincertissuerdn TEXT, 
  reqadmincertsn TEXT, 
  status INT4 NOT NULL, 
  approvaldata TEXT, 
  requestdata TEXT, 
  requestdate INT8 NOT NULL, 
  expiredate INT8 NOT NULL, 
  remainingapprovals INT4 NOT NULL, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_approvaldata PRIMARY KEY (id)
);

DROP TABLE AUTHORIZATIONTREEUPDATEDATA;

CREATE TABLE authorizationtreeupdatedata (
  pK INT4 NOT NULL, 
  authorizationTreeUpdateNumber INT4 NOT NULL, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_authorizationtreeupdatedata PRIMARY KEY (pK)
);

DROP TABLE CADATA;

CREATE TABLE cadata (
  cAId INT4 NOT NULL, 
  name TEXT, 
  subjectDN TEXT, 
  status INT4 NOT NULL, 
  expireTime INT8 NOT NULL, 
  updateTime INT8 NOT NULL, 
  data TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_cadata PRIMARY KEY (cAId)
);

DROP TABLE CERTIFICATEDATA;

CREATE TABLE certificatedata (
  fingerprint TEXT NOT NULL, 
  issuerDN TEXT, 
  subjectDN TEXT, 
  cAFingerprint TEXT, 
  status INT4 NOT NULL, 
  type INT4 NOT NULL, 
  serialNumber TEXT, 
  expireDate INT8 NOT NULL, 
  revocationDate INT8 NOT NULL, 
  revocationReason INT4 NOT NULL, 
  base64Cert TEXT, 
  username TEXT, 
  tag TEXT, 
  certificateProfileId INT4, 
  updateTime INT8 NOT NULL, 
  subjectKeyId TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_certificatedata PRIMARY KEY (fingerprint)
);

DROP TABLE CERTIFICATEPROFILEDATA;

CREATE TABLE certificateprofiledata (
  id INT4 NOT NULL, 
  certificateProfileName TEXT, 
  data BYTEA, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_certificateprofiledata PRIMARY KEY (id)
);

DROP TABLE CERTREQHISTORYDATA;

CREATE TABLE certreqhistorydata (
  fingerprint TEXT NOT NULL, 
  issuerDN TEXT, 
  serialNumber TEXT, 
  timestamp INT8 NOT NULL, 
  userDataVO TEXT, 
  username TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_certreqhistorydata PRIMARY KEY (fingerprint)
);

DROP TABLE CRLDATA;

CREATE TABLE crldata (
  fingerprint TEXT NOT NULL, 
  cRLNumber INT4 NOT NULL, 
  deltaCRLIndicator INT4 NOT NULL, 
  issuerDN TEXT, 
  cAFingerprint TEXT, 
  thisUpdate INT8 NOT NULL, 
  nextUpdate INT8 NOT NULL, 
  base64Crl TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_crldata PRIMARY KEY (fingerprint)
);

DROP TABLE ENDENTITYPROFILEDATA;

CREATE TABLE endentityprofiledata (
  id INT4 NOT NULL, 
  profileName TEXT, 
  data BYTEA, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_endentityprofiledata PRIMARY KEY (id)
);

DROP TABLE GLOBALCONFIGURATIONDATA;

CREATE TABLE globalconfigurationdata (
  configurationId TEXT NOT NULL, 
  data BYTEA, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_globalconfigurationdata PRIMARY KEY (configurationId)
);

DROP TABLE HARDTOKENCERTIFICATEMAP;

CREATE TABLE hardtokencertificatemap (
  certificateFingerprint TEXT NOT NULL, 
  tokenSN TEXT, 
  rowVersion INT4 DEFAULT 0, 
 CONSTRAINT pk_hardtokencertificatemap PRIMARY KEY (certificateFingerprint)
);

DROP TABLE HARDTOKENDATA;

CREATE TABLE hardtokendata (
  tokenSN TEXT NOT NULL, 
  username TEXT, 
  cTime INT8 NOT NULL, 
  mTime INT8 NOT NULL, 
  tokenType INT4 NOT NULL, 
  significantIssuerDN TEXT, 
  data BYTEA, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_hardtokendata PRIMARY KEY (tokenSN)
);

DROP TABLE HARDTOKENISSUERDATA;

CREATE TABLE hardtokenissuerdata (
  id INT4 NOT NULL, 
  alias TEXT, 
  adminGroupId INT4 NOT NULL, 
  data BYTEA, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_hardtokenissuerdata PRIMARY KEY (id)
);

DROP TABLE HARDTOKENPROFILEDATA;

CREATE TABLE hardtokenprofiledata (
  id INT4 NOT NULL, 
  name TEXT, 
  updateCounter INT4 NOT NULL, 
  data TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_hardtokenprofiledata PRIMARY KEY (id)
);

DROP TABLE HARDTOKENPROPERTYDATA;

CREATE TABLE hardtokenpropertydata (
  id TEXT NOT NULL, 
  property TEXT NOT NULL, 
  value TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_hardtokenpropertydata PRIMARY KEY (id, property)
);

DROP TABLE KEYRECOVERYDATA;

CREATE TABLE keyrecoverydata (
  certSN TEXT NOT NULL, 
  issuerDN TEXT NOT NULL, 
  username TEXT, 
  markedAsRecoverable BOOLEAN DEFAULT FALSE NOT NULL, 
  keyData TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_keyrecoverydata PRIMARY KEY (certSN, issuerDN)
);

DROP TABLE LOGCONFIGURATIONDATA;

CREATE TABLE logconfigurationdata (
  id INT4 NOT NULL, 
  logConfiguration BYTEA, 
  logEntryRowNumber INT4 NOT NULL, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_logconfigurationdata PRIMARY KEY (id)
);

DROP TABLE LOGENTRYDATA;

CREATE TABLE logentrydata (
  id INT4 NOT NULL, 
  adminType INT4 NOT NULL, 
  adminData TEXT, 
  caId INT4 NOT NULL, 
  module INT4 NOT NULL, 
  time INT8 NOT NULL, 
  username TEXT, 
  certificateSNR TEXT, 
  event INT4 NOT NULL, 
  logComment TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_logentrydata PRIMARY KEY (id)
);

DROP TABLE PUBLISHERDATA;

CREATE TABLE publisherdata (
  id INT4 NOT NULL, 
  name TEXT, 
  updateCounter INT4 NOT NULL, 
  data TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_publisherdata PRIMARY KEY (id)
);

DROP TABLE PUBLISHERQUEUEDATA;

CREATE TABLE publisherqueuedata (
  pk TEXT NOT NULL, 
  timeCreated INT8 NOT NULL, 
  lastUpdate INT8 NOT NULL,
  publishStatus INT4 NOT NULL,
  tryCounter INT4 NOT NULL,
  publishType INT4 NOT NULL,
  fingerprint TEXT,
  publisherId INT4 NOT NULL,
  volatileData TEXT,
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_publisherqueuedata PRIMARY KEY (pk)
);

DROP TABLE SERVICEDATA;

CREATE TABLE servicedata (
  id INT4 NOT NULL,
  name TEXT, 
  data TEXT, 
  nextRunTimeStamp INT8 NOT NULL DEFAULT 0,  
  runTimeStamp INT8 NOT NULL DEFAULT 0,  
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_servicedata PRIMARY KEY (id)
);

DROP TABLE USERDATA;

CREATE TABLE userdata (
  username TEXT NOT NULL, 
  subjectDN TEXT, 
  cAId INT4 NOT NULL, 
  subjectAltName TEXT, 
  subjectEmail TEXT, 
  status INT4 NOT NULL, 
  type INT4 NOT NULL, 
  clearPassword TEXT, 
  passwordHash TEXT, 
  timeCreated INT8 NOT NULL, 
  timeModified INT8 NOT NULL, 
  endEntityProfileId INT4 NOT NULL, 
  certificateProfileId INT4 NOT NULL, 
  tokenType INT4 NOT NULL, 
  hardTokenIssuerId INT4 NOT NULL, 
  extendedInformationData TEXT, 
  keyStorePassword TEXT, 
  cardnumber TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_userdata PRIMARY KEY (username)
);

DROP TABLE USERDATASOURCEDATA;

CREATE TABLE userdatasourcedata (
  id INT4 NOT NULL, 
  name TEXT, 
  updateCounter INT4 NOT NULL, 
  data TEXT, 
  rowVersion INT4 DEFAULT 0, 
CONSTRAINT pk_userdatasourcedata PRIMARY KEY (id)
);
