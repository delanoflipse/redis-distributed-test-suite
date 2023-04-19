# Fix for ssh errors for 9 nodes.
# Use more nodes? You know what to do.
# Think this should be a loop? Write a PR.

ssh-keyscan -t rsa n1 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n2 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n3 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n4 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n5 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n6 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n7 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n8 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n9 >> ~/.ssh/known_hosts