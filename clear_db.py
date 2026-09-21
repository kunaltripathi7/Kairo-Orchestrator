import psycopg2

try:
    conn = psycopg2.connect("dbname=kairo user=kairo password=kairo host=localhost port=5433")
    cur = conn.cursor()
    cur.execute("TRUNCATE TABLE workflows CASCADE;")
    cur.execute("TRUNCATE TABLE tasks CASCADE;")
    conn.commit()
    cur.close()
    conn.close()
    print("Database cleared successfully.")
except Exception as e:
    print(f"Error: {e}")
