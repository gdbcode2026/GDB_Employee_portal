CREATE USER gdb_audit WITH PASSWORD 'local-development-only';
CREATE DATABASE audit_db OWNER gdb_audit;
CREATE USER gdb_notification WITH PASSWORD 'local-development-only';
CREATE DATABASE notification_db OWNER gdb_notification;
CREATE USER gdb_organization WITH PASSWORD 'local-development-only';
CREATE DATABASE organization_db OWNER gdb_organization;
CREATE USER gdb_employee WITH PASSWORD 'local-development-only';
CREATE DATABASE employee_db OWNER gdb_employee;
