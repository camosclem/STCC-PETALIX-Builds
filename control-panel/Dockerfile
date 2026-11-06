FROM python:3.12-slim
WORKDIR /srv/petalix
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY panel ./panel
EXPOSE 8080
CMD ["uvicorn", "panel.app:app", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers"]
