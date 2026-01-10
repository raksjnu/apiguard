import csv
import random
from datetime import datetime, timedelta

# Generate Salsify_Extract.csv with 150 rows
print("Generating Salsify_Extract.csv with 150 rows...")
with open('sample/Salsify_Extract.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['product_id', 'product_name', 'category', 'brand', 'price', 'stock_quantity', 'last_updated'])
    
    categories = ['Electronics', 'Clothing', 'Home & Garden', 'Sports', 'Books', 'Toys', 'Food & Beverage']
    brands = ['BrandA', 'BrandB', 'BrandC', 'BrandD', 'BrandE', 'BrandF']
    
    for i in range(1, 151):
        product_id = f'SAL{i:05d}'
        product_name = f'Product {i} - {random.choice(categories)}'
        category = random.choice(categories)
        brand = random.choice(brands)
        price = round(random.uniform(9.99, 999.99), 2)
        stock = random.randint(0, 500)
        date = (datetime.now() - timedelta(days=random.randint(0, 365))).strftime('%Y-%m-%d')
        
        writer.writerow([product_id, product_name, category, brand, price, stock, date])

print("✓ Salsify_Extract.csv created with 150 rows")

# Generate products_export.csv with 200 rows
print("\nGenerating products_export.csv with 200 rows...")
with open('sample/products_export.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['product_id', 'product_description', 'master_sku', 'color', 'size', 'weight_kg', 'dimensions'])
    
    colors = ['Red', 'Blue', 'Green', 'Black', 'White', 'Yellow', 'Purple', 'Orange', 'Pink', 'Gray']
    sizes = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'One Size', 'N/A']
    
    for i in range(1, 201):
        product_id = f'PROD{i:05d}'
        description = f'High-quality product item {i} with premium features'
        master_sku = f'MSK-{i:06d}'
        color = random.choice(colors)
        size = random.choice(sizes)
        weight = round(random.uniform(0.1, 50.0), 2)
        dims = f'{random.randint(10,100)}x{random.randint(10,100)}x{random.randint(5,50)}cm'
        
        writer.writerow([product_id, description, master_sku, color, size, weight, dims])

print("✓ products_export.csv created with 200 rows")

# Generate product plant storage location and cogs.csv with 180 rows
print("\nGenerating product plant storage location and cogs.csv with 180 rows...")
with open('sample/product plant storage location and cogs.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['PDA Product Code', 'Plant', 'Storage_Location', 'Cogs_Price', 'Currency', 'Warehouse_Zone', 'Bin_Location'])
    
    plants = ['Plant_001', 'Plant_002', 'Plant_003', 'Plant_004', 'Plant_005']
    storage_locs = ['SL-A', 'SL-B', 'SL-C', 'SL-D', 'SL-E', 'SL-F']
    currencies = ['USD', 'EUR', 'GBP', 'JPY', 'CAD']
    zones = ['Zone-A1', 'Zone-A2', 'Zone-B1', 'Zone-B2', 'Zone-C1', 'Zone-C2']
    
    for i in range(1, 181):
        pda_code = f'PDA{i:05d}'
        plant = random.choice(plants)
        storage = random.choice(storage_locs)
        cogs = round(random.uniform(5.0, 500.0), 2)
        currency = random.choice(currencies)
        zone = random.choice(zones)
        bin_loc = f'BIN-{random.randint(1,999):03d}-{random.choice(["A","B","C","D"])}'
        
        writer.writerow([pda_code, plant, storage, cogs, currency, zone, bin_loc])

print("✓ product plant storage location and cogs.csv created with 180 rows")

# Generate inventory_levels.csv with 120 rows (new file)
print("\nGenerating inventory_levels.csv with 120 rows...")
with open('sample/inventory_levels.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['sku', 'warehouse_id', 'available_qty', 'reserved_qty', 'damaged_qty', 'last_count_date', 'reorder_point'])
    
    warehouses = ['WH-001', 'WH-002', 'WH-003', 'WH-004']
    
    for i in range(1, 121):
        sku = f'SKU{i:05d}'
        warehouse = random.choice(warehouses)
        available = random.randint(0, 1000)
        reserved = random.randint(0, 100)
        damaged = random.randint(0, 20)
        count_date = (datetime.now() - timedelta(days=random.randint(0, 30))).strftime('%Y-%m-%d')
        reorder = random.randint(50, 200)
        
        writer.writerow([sku, warehouse, available, reserved, damaged, count_date, reorder])

print("✓ inventory_levels.csv created with 120 rows")

# Generate supplier_catalog.csv with 100 rows (new file)
print("\nGenerating supplier_catalog.csv with 100 rows...")
with open('sample/supplier_catalog.csv', 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['supplier_id', 'supplier_name', 'product_code', 'supplier_sku', 'unit_price', 'moq', 'lead_time_days'])
    
    suppliers = [
        ('SUP001', 'Global Supplies Inc'),
        ('SUP002', 'Premium Products Ltd'),
        ('SUP003', 'Quality Goods Corp'),
        ('SUP004', 'Best Value Suppliers'),
        ('SUP005', 'Elite Manufacturing')
    ]
    
    for i in range(1, 101):
        supplier = random.choice(suppliers)
        product_code = f'PC{i:05d}'
        supplier_sku = f'{supplier[0]}-{random.randint(1000,9999)}'
        unit_price = round(random.uniform(1.0, 200.0), 2)
        moq = random.choice([10, 25, 50, 100, 250, 500])
        lead_time = random.randint(7, 90)
        
        writer.writerow([supplier[0], supplier[1], product_code, supplier_sku, unit_price, moq, lead_time])

print("✓ supplier_catalog.csv created with 100 rows")

print("\n" + "="*60)
print("✅ ALL TEST FILES GENERATED SUCCESSFULLY!")
print("="*60)
print("\nSummary:")
print("  • Salsify_Extract.csv: 150 rows")
print("  • products_export.csv: 200 rows")
print("  • product plant storage location and cogs.csv: 180 rows")
print("  • inventory_levels.csv: 120 rows (NEW)")
print("  • supplier_catalog.csv: 100 rows (NEW)")
print("\nTotal: 750 rows across 5 files")
print("\nYou can now test bulk transformations with these files!")
