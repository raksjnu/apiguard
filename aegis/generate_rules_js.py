import yaml
import json
import os

def yaml_to_js(yaml_path, js_path):
    print(f"Reading YAML from {yaml_path}")
    if not os.path.exists(yaml_path):
        print("Error: rules.yaml not found at", yaml_path)
        return

    with open(yaml_path, 'r') as stream:
        try:
            data = yaml.safe_load(stream)
            
            # Extract just the rules list and config summary
            rules = data.get('rules', [])
            config = data.get('config', {})
            
            output_data = {
                "generatedAt": "Dynamic",
                "rules": rules,
                "config": config
            }
            
            js_content = f"const AEGIS_RULES_DATA = {json.dumps(output_data, indent=2)};"
            
            with open(js_path, 'w') as js_file:
                js_file.write(js_content)
                
            print(f"Successfully created {js_path}")
            
        except yaml.YAMLError as exc:
            print(exc)

# Adjust paths as needed for the environment
BASE_DIR = r"C:\raks\apiguard\aegis"
YAML_FILE = os.path.join(BASE_DIR, "src", "main", "resources", "rules", "rules.yaml")
JS_FILE = os.path.join(BASE_DIR, "rules_data.js")

if __name__ == "__main__":
    yaml_to_js(YAML_FILE, JS_FILE)
