import yaml

def validate_rules(yaml_path):
    with open(yaml_path, 'r') as f:
        data = yaml.safe_load(f)
    
    rules = data.get('rules', [])
    print(f"Found {len(rules)} rules.")
    
    for rule in rules:
        rule_id = rule.get('id', 'UNKNOWN_ID')
        checks = rule.get('checks')
        if checks is None:
            print(f"ERROR: Rule {rule_id} is missing 'checks' property or it is null.")
        elif not isinstance(checks, list):
             print(f"ERROR: Rule {rule_id} 'checks' is not a list.")
        else:
            # print(f"Rule {rule_id} OK.")
            pass

if __name__ == "__main__":
    validate_rules('src/main/resources/rules/rules.yaml')
