FROM python:2.7-alpine

WORKDIR /usr/src/app
COPY requirements.txt ./
RUN pip install -r requirements.txt
