#!/bin/bash
# Start the API Response Comparison Tool GUI

echo "Starting API Response Comparison Tool - GUI..."
echo "The GUI will open automatically in your default browser."
echo ""

# Run the GUI web server by explicitly specifying the ApiUrlComparisonWeb class
java -cp target/apiurlcomparison-1.0.0-jar-with-raks.jar com.raks.apiurlcomparison.ApiUrlComparisonWeb

# Keep the terminal open if there's an error
if [ $? -ne 0 ]; then
    echo ""
    echo "Press any key to exit..."
    read -n 1
fi
