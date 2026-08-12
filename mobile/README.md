# Pixous HR — Mobile (Expo)

The employee companion app: punch attendance with GPS, check leave balances and
requests, and view payslips on the go.

## Run

```bash
npm install
npx expo start
```

Then open with **Expo Go** on your phone (scan the QR), or press `a` / `i` for an
Android / iOS emulator.

## API connection

The app reads the backend URL from `EXPO_PUBLIC_API_URL` (see `.env.example`).
On a physical device, use your computer's LAN IP — a phone cannot reach
`localhost` on your laptop:

```
EXPO_PUBLIC_API_URL=http://192.168.1.5:8081
```

## Screens

- **Home** — today's punch status, quick stats, leave balances, recent activity
- **Attendance** — punch in/out; office and site modes capture GPS for geofencing
- **Leave** — balances and your request history
- **Payslips** — monthly net/gross summary (download PDFs from the web portal)

Login is by 12-digit Aadhaar. Demo: `123456789022` / `Test1234@`.
