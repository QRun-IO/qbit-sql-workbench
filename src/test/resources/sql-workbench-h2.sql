-- H2 (MODE=MySQL) version of docs/ddl/postgres.sql, for unit tests.
DROP TABLE IF EXISTS sql_workbench_notebook_share;
DROP TABLE IF EXISTS sql_workbench_result_snapshot;
DROP TABLE IF EXISTS sql_workbench_notebook_version;
DROP TABLE IF EXISTS sql_workbench_cell;
DROP TABLE IF EXISTS sql_workbench_notebook;
DROP TABLE IF EXISTS sql_workbench_execution;
DROP TABLE IF EXISTS person;

CREATE TABLE IF NOT EXISTS sql_workbench_notebook (
   id INTEGER AUTO_INCREMENT NOT NULL,
   title VARCHAR(255) NOT NULL,
   description VARCHAR(1000000),
   owner_user_id VARCHAR(255) NOT NULL,
   owner_name VARCHAR(255),
   visibility VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
   default_backend_name VARCHAR(100),
   parameters_json VARCHAR(1000000),
   current_version INTEGER,
   create_date TIMESTAMP,
   modify_date TIMESTAMP,
   CONSTRAINT pk_sql_workbench_notebook PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS ix_sql_workbench_notebook_owner ON sql_workbench_notebook (owner_user_id);
CREATE INDEX IF NOT EXISTS ix_sql_workbench_notebook_visibility ON sql_workbench_notebook (visibility);

CREATE TABLE IF NOT EXISTS sql_workbench_cell (
   id INTEGER AUTO_INCREMENT NOT NULL,
   notebook_id INTEGER NOT NULL,
   cell_key VARCHAR(64) NOT NULL,
   sort_order INTEGER,
   cell_type VARCHAR(16) NOT NULL,
   title VARCHAR(255),
   content VARCHAR(1000000),
   backend_name VARCHAR(100),
   create_date TIMESTAMP,
   modify_date TIMESTAMP,
   CONSTRAINT pk_sql_workbench_cell PRIMARY KEY (id),
   CONSTRAINT uq_sql_workbench_cell_key UNIQUE (notebook_id, cell_key),
   CONSTRAINT fk_sql_workbench_cell_notebook FOREIGN KEY (notebook_id) REFERENCES sql_workbench_notebook (id)
);

CREATE TABLE IF NOT EXISTS sql_workbench_notebook_version (
   id INTEGER AUTO_INCREMENT NOT NULL,
   notebook_id INTEGER NOT NULL,
   version_number INTEGER NOT NULL,
   content_json VARCHAR(1000000),
   saved_by_user_id VARCHAR(255),
   saved_by_name VARCHAR(255),
   comment VARCHAR(255),
   create_date TIMESTAMP,
   CONSTRAINT pk_sql_workbench_notebook_version PRIMARY KEY (id),
   CONSTRAINT uq_sql_workbench_notebook_version UNIQUE (notebook_id, version_number),
   CONSTRAINT fk_sql_workbench_notebook_version_notebook FOREIGN KEY (notebook_id) REFERENCES sql_workbench_notebook (id)
);

CREATE TABLE IF NOT EXISTS sql_workbench_result_snapshot (
   id INTEGER AUTO_INCREMENT NOT NULL,
   notebook_id INTEGER NOT NULL,
   cell_key VARCHAR(64),
   execution_uuid VARCHAR(36),
   label VARCHAR(255),
   backend_name VARCHAR(100),
   sql_text VARCHAR(1000000),
   result_json VARCHAR(1000000),
   row_count INTEGER,
   truncated BOOLEAN,
   created_by_user_id VARCHAR(255),
   create_date TIMESTAMP,
   CONSTRAINT pk_sql_workbench_result_snapshot PRIMARY KEY (id),
   CONSTRAINT fk_sql_workbench_result_snapshot_notebook FOREIGN KEY (notebook_id) REFERENCES sql_workbench_notebook (id)
);
-- the notebook page lists its snapshots, and deleting a notebook deletes them, by notebook_id
CREATE INDEX IF NOT EXISTS ix_sql_workbench_result_snapshot_notebook ON sql_workbench_result_snapshot (notebook_id);

CREATE TABLE IF NOT EXISTS sql_workbench_notebook_share (
   id INTEGER AUTO_INCREMENT NOT NULL,
   notebook_id INTEGER NOT NULL,
   audience_type VARCHAR(16) NOT NULL,
   audience_id VARCHAR(255),
   audience_key VARCHAR(300) NOT NULL,
   created_by_user_id VARCHAR(255),
   created_by_name VARCHAR(255),
   create_date TIMESTAMP,
   modify_date TIMESTAMP,
   CONSTRAINT pk_sql_workbench_notebook_share PRIMARY KEY (id),
   CONSTRAINT uq_sql_workbench_notebook_share UNIQUE (notebook_id, audience_key),
   CONSTRAINT fk_sql_workbench_notebook_share_notebook FOREIGN KEY (notebook_id) REFERENCES sql_workbench_notebook (id)
);

-- Audit: append-only. Not foreign-keyed to notebooks, so deleting a
-- notebook never deletes its audit trail.
CREATE TABLE IF NOT EXISTS sql_workbench_execution (
   id INTEGER AUTO_INCREMENT NOT NULL,
   execution_uuid VARCHAR(36) NOT NULL,
   user_id VARCHAR(255),
   user_name VARCHAR(255),
   backend_name VARCHAR(100),
   mode VARCHAR(24),
   status VARCHAR(16),
   sql_text VARCHAR(1000000),
   sql_hash VARCHAR(64),
   parameters_json VARCHAR(1000000),
   statement_count INTEGER,
   statement_kinds VARCHAR(255),
   notebook_id INTEGER,
   cell_key VARCHAR(64),
   start_time TIMESTAMP,
   end_time TIMESTAMP,
   duration_ms INTEGER,
   rows_returned INTEGER,
   rows_affected INTEGER,
   truncated BOOLEAN,
   error_message VARCHAR(1000000),
   client_ip VARCHAR(64),
   create_date TIMESTAMP,
   CONSTRAINT pk_sql_workbench_execution PRIMARY KEY (id),
   CONSTRAINT uq_sql_workbench_execution_uuid UNIQUE (execution_uuid)
);
CREATE INDEX IF NOT EXISTS ix_sql_workbench_execution_user ON sql_workbench_execution (user_id, id);
CREATE INDEX IF NOT EXISTS ix_sql_workbench_execution_start ON sql_workbench_execution (start_time);
-- the notebook page's "Runs" list joins executions by notebook_id
CREATE INDEX IF NOT EXISTS ix_sql_workbench_execution_notebook ON sql_workbench_execution (notebook_id);

CREATE TABLE person (
   id INTEGER AUTO_INCREMENT NOT NULL PRIMARY KEY,
   first_name VARCHAR(80),
   last_name VARCHAR(80),
   birth_date DATE,
   salary DECIMAL(12, 2),
   active BOOLEAN,
   photo VARBINARY(1000),
   create_date TIMESTAMP
);
INSERT INTO person (first_name, last_name, birth_date, salary, active, photo, create_date) VALUES
   ('Darin', 'Kelkhoff', DATE '1980-05-31', 25000.00, TRUE, X'CAFEBABE', TIMESTAMP '2026-01-01 10:00:00'),
   ('James', 'Maes', DATE '1981-02-02', 26000.50, TRUE, NULL, TIMESTAMP '2026-01-02 11:00:00'),
   ('Tim', 'Chamberlain', DATE '1976-03-03', NULL, FALSE, NULL, TIMESTAMP '2026-01-03 12:00:00'),
   ('Tyler', 'Samples', NULL, 30000.00, TRUE, NULL, NULL),
   ('Garret', 'Richardson', DATE '1985-04-04', 19000.00, NULL, NULL, TIMESTAMP '2026-01-05 14:00:00');
