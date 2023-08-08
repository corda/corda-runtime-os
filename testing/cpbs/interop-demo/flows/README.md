Copy deploy.sh and other scripts (if needed) at the same level as this project directory.
You may need to assign executable permissions to the scripts  (`chmod 777 ./deploy.sh`)
Run:
``
source deploy.sh || true
``
After the scrip completes, you can run flows in the same terminal 
(they will reuse variables created by deploy.sh script):
``
source flowv1.sh || true
``
