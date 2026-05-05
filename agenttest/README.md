# Agent Test App

`agenttest` is a standalone manual testing app for amanOS modules before a unified agent app exists.

## Current testers
- Contacts
- Messaging

## How it works
- sends action broadcasts to installed module apps
- queries exported module content providers through `AgentContract`
- receives result callbacks through reply extras
- listens to module event broadcasts and shows a live log

## Add a new module later
1. Add a new entry to `TesterRegistry`
2. Add a new tester screen composable
3. Add any event actions you want to observe in `MainActivity`

## Build

```powershell
cd "E:\AndroidProjects\amanOS"
.\gradlew.bat :agenttest:assembleDebug
```

