#!/bin/bash
cd /home/traceback/.openclaw/workspace/ecl-project/ecl-system/ecl-frontend
exec env PORT=3000 npx vite --host 0.0.0.0 >> /home/traceback/.openclaw/workspace/ecl-project/ecl-system/logs/frontend.log 2>&1
