import csv
import os

source_dir = r"C:\raks\apiguard\filesync\source"
os.makedirs(source_dir, exist_ok=True)

# 1. Salsify_Extract.csv
# Columns: Launch Quarter, Season, product_category
with open(os.path.join(source_dir, "Salsify_Extract.csv"), "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(["Launch Quarter", "Season", "product_category"])
    writer.writerow(["Q2", "1H25", "Hard Cooler"])
    writer.writerow(["Q3", "2H25", "Soft Cooler"])

# 2. products_export.csv
# Columns: Derived, Derived - master_sku|| ' ' || color, category_type, color, master_sku, price, product_description, product_hierarchy_id, product_id, product_type, upc
with open(os.path.join(source_dir, "products_export.csv"), "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    # Using the columns identified from mapping
    headers = ["Derived", "Derived - master_sku|| ' ' || color", "category_type", "color", "master_sku", "price", "product_description", "product_hierarchy_id", "product_id", "product_type", "upc"]
    writer.writerow(headers)
    # Sample row matching the requirement.txt logic roughly
    # product_id=123, product_Type=Roadie
    writer.writerow(["", "", "Hard Cooler Core", "Charcoal", "Roadie 24 2.0", "$162.50", "Roadie 24 2.0 Charcoal", "1.0101E+15", "10022160002", "Roadie", "8.8883E+11"])
    writer.writerow(["", "", "Soft Cooler Core", "Navy", "Hopper 20", "$200.00", "Hopper 20 Navy", "1.0102E+15", "70000004339", "Hopper", "8.8883E+12"])

# 3. product plant storage location and cogs.csv
# Columns: Cogs_Price
with open(os.path.join(source_dir, "product plant storage location and cogs.csv"), "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(["Cogs_Price", "FDA Product Code"])
    writer.writerow(["100.00", "8.8883E+11"])

print("Files created.")
