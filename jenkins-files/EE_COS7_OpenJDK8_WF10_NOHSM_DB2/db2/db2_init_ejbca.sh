#!/bin/sh -x

# switch to db2inst1
sudo -i -u db2inst1 bash -x << EOF
# Database created by docker
#db2 create database ejbca
# Connect to ejbca
db2 connect to ejbca
# Create temporary pools of a different size eg. for a temporary table
db2 CREATE BUFFERPOOL TBP4K pagesize 4K
db2 CREATE SYSTEM TEMPORARY TABLESPACE STB4K PAGESIZE 4K BUFFERPOOL TBP4K
db2 CREATE BUFFERPOOL TBP8K pagesize 8K
db2 CREATE SYSTEM TEMPORARY TABLESPACE STB8K PAGESIZE 8K BUFFERPOOL TBP8K
db2 CREATE BUFFERPOOL TBP16K pagesize 16K
db2 CREATE SYSTEM TEMPORARY TABLESPACE STB16K PAGESIZE 16K BUFFERPOOL TBP16K
db2 CREATE BUFFERPOOL TBP32K pagesize 32K
db2 CREATE SYSTEM TEMPORARY TABLESPACE STB32K PAGESIZE 32K BUFFERPOOL TBP32K
# Create regular buffer pool and table space storing it in the home folder of docker container (db2inst1)
db2 CREATE BUFFERPOOL BP16K SIZE 2500 PAGESIZE 16K
db2 "CREATE REGULAR TABLESPACE EJBCADB_DATA_01 PAGESIZE 16K EXTENTSIZE 32 PREFETCHSIZE 32 BUFFERPOOL BP16K OVERHEAD 7.500000 FILE SYSTEM CACHING TRANSFERRATE 0.060000 DROPPED TABLE RECOVERY OFF"
# To output db2 pools, uncomment next line
# db2 "select TBSP_NAME,TBSP_PAGE_SIZE,TBSP_STATE from TABLE(MON_GET_TABLESPACE('',-2))"
# Add EJBCA indexes
db2 -f /var/custom/create-tables-ejbca-db2.sql
db2 -f /var/custom/create-index-ejbca.sql
db2 'CREATE INDEX userdata_idx_test1 ON UserData (cAId)'
# Close connection
db2 connect reset
EOF
