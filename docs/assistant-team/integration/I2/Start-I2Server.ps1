$ErrorActionPreference = 'Stop'
# Preserve PowerShell's native argv handling for the approved Chinese Windows path.
& 'D:\知识图谱-i2-env\mysql-8.4.11-winx64\bin\mysqld.exe' '--defaults-file=D:/知识图谱-i2-env/mysql-i2.ini'
exit $LASTEXITCODE
