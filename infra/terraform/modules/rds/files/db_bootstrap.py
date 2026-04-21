import json
import pg8000

def handler(event, context):
    # Credenciais e host chegam diretamente no evento (enviadas pelo
    # Terraform a partir do estado). Evita dependencia de VPC endpoint /
    # NAT para alcancar o Secrets Manager durante o bootstrap.
    admin = event["Admin"]
    host = admin["host"]
    port = admin["port"]
    admin_user = admin["username"]
    admin_pass = admin["password"]
    databases = event["Databases"]
    
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
                db_pass_escaped = db_pass.replace("'", "''")
                cur.execute(f'CREATE ROLE "{db_user}" WITH LOGIN PASSWORD \'{db_pass_escaped}\'')
                results.append(f"Created role {db_user}")
            else:
                db_pass_escaped = db_pass.replace("'", "''")
                cur.execute(f'ALTER ROLE "{db_user}" WITH LOGIN PASSWORD \'{db_pass_escaped}\'')
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
