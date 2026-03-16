import base64
import os
import shutil
import io
import time
from flask import Flask, request, jsonify, send_from_directory
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from PIL import Image

app = Flask(__name__)
UPLOAD_FOLDER = 'lore_memories'
LAPTOP_IP = "10.69.116.146" 

PEOPLE_DIR = os.path.join(UPLOAD_FOLDER, "People_Detected")
UNNAMED_DIR = os.path.join(UPLOAD_FOLDER, "Unnamed_or_No_Faces")

for folder in [PEOPLE_DIR, UNNAMED_DIR]:
    if not os.path.exists(folder):
        os.makedirs(folder)

# Initialize AI
current_dir = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(current_dir, 'detector.tflite')
with open(MODEL_PATH, "rb") as f:
    model_data = f.read()

detector = vision.FaceDetector.create_from_options(
    vision.FaceDetectorOptions(base_options=python.BaseOptions(model_asset_buffer=model_data))
)

def get_thumbnail_base64(folder_name):
    folder_path = os.path.join(UPLOAD_FOLDER, folder_name)
    if not os.path.exists(folder_path) or not os.listdir(folder_path):
        return ""
    try:
        files = [os.path.join(folder_path, f) for f in os.listdir(folder_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if not files: return ""
        latest_file = max(files, key=os.path.getmtime)
        with Image.open(latest_file) as img:
            img.thumbnail((300, 300))
            buffered = io.BytesIO()
            img.save(buffered, format="JPEG", quality=70)
            return base64.b64encode(buffered.getvalue()).decode('utf-8')
    except:
        return ""

@app.route('/status', methods=['GET'])
def get_status():
    return jsonify({
        "people": len(os.listdir(PEOPLE_DIR)),
        "unnamed": len(os.listdir(UNNAMED_DIR)),
        "people_thumb": get_thumbnail_base64("People_Detected"),
        "unnamed_thumb": get_thumbnail_base64("Unnamed_or_No_Faces"),
        "ts": int(time.time())
    })

@app.route('/upload', methods=['POST'])
def upload_file():
    files = request.files.getlist('images')
    for file in files:
        unique_name = f"{int(time.time()*1000)}_{file.filename}"
        temp_path = os.path.join(UPLOAD_FOLDER, unique_name)
        file.save(temp_path)
        try:
            image = mp.Image.create_from_file(temp_path)
            face_count = len(detector.detect(image).detections)
            target = PEOPLE_DIR if face_count > 0 else UNNAMED_DIR
            shutil.move(temp_path, os.path.join(target, unique_name))
        except:
            if os.path.exists(temp_path): os.remove(temp_path)
    return get_status()

@app.route('/gallery/<folder_key>')
def get_gallery_list(folder_key):
    actual = "People_Detected" if folder_key == "People" else "Unnamed_or_No_Faces"
    target = os.path.join(UPLOAD_FOLDER, actual)
    files = sorted(os.listdir(target), key=lambda x: os.path.getmtime(os.path.join(target, x)), reverse=True)
    return jsonify([f"http://{LAPTOP_IP}:8080/file/{actual}/{f}" for f in files])

@app.route('/file/<folder>/<filename>')
def serve_file(folder, filename):
    return send_from_directory(os.path.join(UPLOAD_FOLDER, folder), filename)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=False, use_reloader=False)