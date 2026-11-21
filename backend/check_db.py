import psycopg2
conn = psycopg2.connect(host='localhost', dbname='halal_jakim', user='postgres', password='Huffazpro1!')
cur = conn.cursor()
cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name='halal_products'")
cols = [r[0] for r in cur.fetchall()]
print('Columns:', cols)
cur.execute('SELECT COUNT(*) FROM halal_products')
print('Total rows:', cur.fetchone()[0])
for col in cols:
    if col in ('product_category','category','jenis','type','classification','product_type','halal_category'):
        cur.execute(f'SELECT {col}, COUNT(*) FROM halal_products GROUP BY {col} ORDER BY COUNT(*) DESC LIMIT 15')
        print(f'\nBy {col}:')
        for row in cur.fetchall():
            print(f'  {row[0]}: {row[1]:,}')
conn.close()
