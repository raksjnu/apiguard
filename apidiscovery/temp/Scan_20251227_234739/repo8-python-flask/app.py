from flask import Flask
app = Flask(__name__)

@app.route('/api/data')
def get_data():
    return "Hello World"
