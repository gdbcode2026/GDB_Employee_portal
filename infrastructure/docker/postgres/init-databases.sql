CREATE USER gdb_audit WITH PASSWORD 'local-development-only';
CREATE DATABASE audit_db OWNER gdb_audit;
CREATE USER gdb_notification WITH PASSWORD 'local-development-only';
CREATE DATABASE notification_db OWNER gdb_notification;
