FROM jenkins/ssh-agent:7.16.0-jdk21

# Test only. Safe to connect with user/password
RUN echo "password\npassword" | passwd root
RUN sed -i 's/PasswordAuthentication no/PasswordAuthentication yes/g' /etc/ssh/sshd_config
RUN sed -i 's/PermitRootLogin no/PermitRootLogin yes/g' /etc/ssh/sshd_config
