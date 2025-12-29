# Flask Configuration
DEBUG = True
SECRET_KEY = 'dev-secret-key-not-for-production'
SQLALCHEMY_DATABASE_URI = 'postgresql://admin:admin123@localhost/flaskdb'
JWT_SECRET_KEY = 'jwt-secret-12345'
# API Keys
STRIPE_API_KEY = 'sk_test_placeholder_key'
SENDGRID_API_KEY = 'SG.1234567890abcdefghijklmnopqrstuvwxyz'
# Redis
REDIS_URL = 'redis://:redis_pass@localhost:6379/0'
