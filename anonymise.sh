#!/bin/bash

files="eBTI_Addresses.csv eBTI_Application.csv tblCaseBTI.csv tblCaseClassMeth.csv tblCaseRecord.csv tblMovement.csv tblUser.csv"
mkdir output
for file in $files
do 
   curl --data-binary @$url -v -XPOST -H "Content-Type:text/plain" -H "Transfer-Encoding: chunked" https://admin.tax.service.gov.uk/binding-tariff-admin/anonymise-data > "./output/$file"
done
