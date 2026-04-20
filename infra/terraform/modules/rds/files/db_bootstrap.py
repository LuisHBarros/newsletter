import json
import os
import boto3
import pg8000

def handler(event, context):
    secret_arn = event["SecretArn"]
    databases = event["Databases"]
    
    sm = boto3.client("secretsmanager")
    secret = json.loads(sm.get_secret_value(SecretId=secret_arn)["SecretString"])
    
    host = secret["host"]
    port = secret["port"]
    admin_user = secret["username"]
    admin_pass = secret["password"]
    
    conn = pg8000.connect(
        host=host,
        port=int(port),
        user=admin_user,
        password=admin_pass,
        database="postgres",
        ssl_context=True,
    )
    conn.autocommit = True
    
    results = []
    
    try:
        for key, db_config in databases.items():
            db_name = db_config["name"]
            db_user = db_config["user"]
            db_pass = db_config["password"]
            
            cur = conn.cursor()
            
            cur.execute(
                "SELECT 1 FROM pg_database WHERE datname = %s",
                (db_name,)
            )
            if cur.fetchone() is None:
                cur.execute(f'CREATE DATABASE "{db_name}"')
                results.append(f"Created database {db_name}")
            else:
                results.append(f"Database {db_name} already exists")
            
            cur.execute(
                "SELECT 1 FROM pg_roles WHERE rolname = %s",
                (db_user,)
            )
            if cur.fetchone() is None:
                cur.execute(
                    f"CREATE ROLE \"{db_user}\" WITH LOGIN PASSWORD %s",
                    (db_pass,)
                )
                results.append(f"Created role {db_user}")
            else:
                cur.execute(
                    f"ALTER ROLE \"{db_user}\" WITH LOGIN PASSWORD %s",
                    (db_pass,)
                )
                results.append(f"Updated password for role {db_user}")
            
            cur.execute(f'GRANT CREATE, USAGE ON SCHEMA public TO "{db_user}"')
            
            cur.execute(
                f"ALTER DEFAULT PRIVILEGES FOR USER \"{admin_user}\" IN SCHEMA public "
                f"GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO \"{db_user}\""
            )
            
            cur.execute(
                f"ALTER DEFAULT PRIVILEGES FOR USER \"{admin_user}\" IN SCHEMA public "
                f"GRANT SELECT, USAGE ON SEQUENCES TO \"{db_user}\""
            )
            
            cur.close()
    finally:
        conn.close()
    
    return {
        "statusCode": 200,
        "body": json.dumps({"results": results})
    }
