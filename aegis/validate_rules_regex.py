import re

def validate_rules(yaml_path):
    print(f"Scanning {yaml_path}...")
    with open(yaml_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    current_rule_id = None
    has_checks = False
    rule_start_line = 0

    for i, line in enumerate(lines):
        line_stripped = line.strip()
        # Detect new rule start
        if line_stripped.startswith('- id:'):
            # Check previous rule
            if current_rule_id:
                if not has_checks:
                     print(f"ERROR: Rule {current_rule_id} (started at line {rule_start_line}) is missing 'checks'!")
                else:
                    # print(f"Rule {current_rule_id} OK.")
                    pass
            
            # Start new rule
            current_rule_id = line_stripped.split('"')[1]
            has_checks = False
            rule_start_line = i + 1
        
        # Check for matching checks key (strict indentation + colon)
        if re.search(r'^\s+checks:', line):
            has_checks = True

    # Check last rule
    if current_rule_id:
        if not has_checks:
             print(f"ERROR: Rule {current_rule_id} (started at line {rule_start_line}) is missing 'checks'!")
        else:
            print("Finished scanning.")

if __name__ == "__main__":
    validate_rules('src/main/resources/rules/rules.yaml')
