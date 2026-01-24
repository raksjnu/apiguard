#!/bin/bash
# Start the Mock API Server

echo "Starting Mock API Server..."
echo ""
echo "Mock servers will start on the following ports:"
echo "  - REST API 1: http://localhost:9091"
echo "  - REST API 2: http://localhost:9092"
echo "  - SOAP API 1: http://localhost:9093"
echo "  - SOAP API 2: http://localhost:9094"
echo ""
echo "Press Ctrl+C to stop the servers."
echo ""

# Run the mock server
mvn compile exec:java -Dexec.mainClass="com.raks.apiforge.MockApiServer"

# Keep the terminal open if there's an error
if [ $? -ne 0 ]; then
    echo ""
    echo "Press any key to exit..."
    read -n 1
fi
