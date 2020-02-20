@ECHO OFF

for %%x in (
        eBTI_Addresses.csv
        eBTI_Application.csv
        tblCaseClassMeth.csv
        tblCaseRecord.csv
        tblMovement.csv
        tblUser.csv
       ) do (

		curl --data-binary @"%%x" -v -XPOST -H "Content-Type:text/plain" -H "Transfer-Encoding: chunked" https://admin.tax.service.gov.uk/binding-tariff-admin/anonymise-data  /all > "%cd%\output\%%x"

       )