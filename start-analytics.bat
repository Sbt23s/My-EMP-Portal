@echo off
echo Starting Analytics Service...
cd analytics-service
pip install -r requirements.txt
python main.py
pause
