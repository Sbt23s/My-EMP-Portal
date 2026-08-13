' Hidden launcher for the HR-Portal auto-deploy watchdog (runs every 5 min via Task Scheduler).
' Runs bash fully hidden (no console window flash).
Set sh = CreateObject("WScript.Shell")
sh.Run "bash -lc ""/c/Users/balas/Documents/product level/GitHub/hr-port/scripts/auto-deploy-watchdog.sh""", 0, False
