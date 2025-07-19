#!/bin/bash

# Must assign public key to snowflake user
# 
# Example:
#     ALTER USER example_user SET RSA_PUBLIC_KEY='MIIBIjANBgkqh...';
# 
# Typically requires adding
# 
#     -Dnet.snowflake.jdbc.enableBouncyCastle=true
# 
# to the client startup to avoid NoSuchAlgorithmException.


# Snowflake MFA setup for interactive human users:
# 
#     USE ROLE ACCOUNTADMIN;
# 
#     ALTER USER mre SET TYPE = PERSON;
# 
#     CREATE DATABASE security_config;
#     USE DATABASE security_config;
#     CREATE SCHEMA policies;
#     USE SCHEMA policies;
#     CREATE AUTHENTICATION POLICY require_mfa_policy
# 	MFA_AUTHENTICATION_METHODS = ('PASSWORD')
# 	MFA_ENROLLMENT = REQUIRED;
#     ALTER ACCOUNT SET AUTHENTICATION POLICY require_mfa_policy;

declare -i RSLT=0

function gen_unencrypted_keys {

	echo "generating unencrypted private key"
	openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -out ${KEYNAME}_nocrypt.p8 -nocrypt
	RSLT+=$?

	echo "generating public key from unencrypted private key"
	openssl rsa -in ${KEYNAME}_nocrypt.p8 -pubout -out ${KEYNAME}_nocrypt.pub
	RSLT+=$?
}

function gen_encrypted_keys {

	echo "generating encrypted private key"
	openssl genrsa 2048 | openssl pkcs8 -topk8 -inform PEM -out ${KEYNAME}_crypt.p8 -passout pass:${PASSPHRASE} # -v2 des3 
	RSLT+=$?

	echo "generating public key from encrypted private key"
	openssl rsa -in ${KEYNAME}_crypt.p8 -passin pass:${PASSPHRASE} -pubout -out ${KEYNAME}_crypt.pub
	RSLT+=$?
}

case $# in
0)
	echo -n "enter user: "
	read SFUSER
	echo -n "enter passphrase: "
	read PASSPHRASE
	;;
*)
	SFUSER=$1
	PASSPHRASE=$2
	;;
esac

KEYNAME=snowflake_aj85646_${SFUSER}_rsa_key

gen_unencrypted_keys
gen_encrypted_keys

case $RSLT in
0)
	echo "unencrypted private key ....... ${KEYNAME}_nocrypt.p8"
	echo "unencrypted public key ........ ${KEYNAME}_nocrypt.pub"
	echo "encrypted private key ......... ${KEYNAME}_crypt.p8"
	echo "encrypted public key .......... ${KEYNAME}_crypt.pub"
	echo
	echo "NOCRYPT public key:"
	grep -v -- '---' ${KEYNAME}_nocrypt.pub | perl -pe 'next if /---/;chomp'
	echo
	echo
	echo "CRYPT public key:"
	grep -v -- '---' ${KEYNAME}_crypt.pub | perl -pe 'next if /---/;chomp'
	echo
	;;
*)
	echo "ERROR: one or more openssl key generation tasks failed"
	exit 255
	;;
esac

