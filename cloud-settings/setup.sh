#!/bin/sh

# Commands to trigger manually after the server and DNS is successfully set up.

certbot --apache -n --agree-tos --email haui@haumacher.de --domains phoneblock2.haumacher.de

