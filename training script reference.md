import os
import csv
import cv2
import json
import zipfile
import numpy as np
import pandas as pd
import tensorflow as tf
import mediapipe as mp
from sklearn.model_selection import train_test_split
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, Dropout
from tensorflow.keras.utils import to_categorical

# ============================================================================
# Configuration: Fixed-Frame Raw Landmarks (132 Features)
# ============================================================================
NUM_FEATURES = 132  # 33 landmarks * (x, y, z, visibility)

def get_raw_features(landmarks):
    """
    Args:
        landmarks: Normalized pose landmarks (0.0 to 1.0).
    Returns:
        A flat list of 132 features.
    """
    features = []
    for lm in landmarks:
        # Extract x, y, z, and visibility.
        # Visibility helps the model understand if a point is occluded (e.g., by a desk).
        features.extend([lm.x, lm.y, lm.z, lm.visibility])
    return features

# Unzip the uploaded dataset
print("Unzipping dataset...")
try:
    with zipfile.ZipFile('posture_dataset.zip', 'r') as zip_ref:
        zip_ref.extractall('.')
    print("✅ Environment ready.")
except FileNotFoundError:
    print("⚠️ Warning: posture_dataset.zip not found. Skipping unzip step.")
    print("✅ Environment ready.")

# @title Step 2: Extract Landmarks to CSV
dataset_path = 'posture_dataset'
output_csv_path = 'train_data.csv'

# Setup MediaPipe
mp_pose = mp.solutions.pose
pose = mp_pose.Pose(
    static_image_mode=True,
    min_detection_confidence=0.5,
    model_complexity=2
)

# Open CSV for writing
with open(output_csv_path, mode='w', newline='') as f:
    csv_writer = csv.writer(f, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)

    # Write Header (class_name + 132 features)
    header = ['class_name'] + [f'feat_{i+1}' for i in range(NUM_FEATURES)]
    csv_writer.writerow(header)

    # Process each folder
    classes = [d for d in os.listdir(dataset_path) if os.path.isdir(os.path.join(dataset_path, d))]
    print(f"Found classes: {classes}")

    for class_name in classes:
        print(f"Processing: {class_name}...")
        class_folder = os.path.join(dataset_path, class_name)

        for image_name in os.listdir(class_folder):
            image_path = os.path.join(class_folder, image_name)
            image = cv2.imread(image_path)
            if image is None: continue

            # Extract Landmarks
            image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            results = pose.process(image_rgb)

            # Use Normalized Landmarks (0-1) for better stability in fixed camera setups
            if results.pose_landmarks:
                lm_list = results.pose_landmarks.landmark
                
                # Generate 132 raw features (x, y, z, visibility for all 33 landmarks)
                features = get_raw_features(lm_list)
                
                row = [class_name] + features
                csv_writer.writerow(row)

print(f"✅ Data saved to {output_csv_path}")

# @title Step 3: Train Neural Network
# --- NEW: Data Augmentation (Noise) ---
def augment_with_noise(X, y, num_fake=10, noise_level=0.005):
    X_aug = []
    y_aug = []
    
    for i in range(len(X)):
        original_sample = X.iloc[i].values
        label = y.iloc[i]
        
        # Add original sample
        X_aug.append(original_sample)
        y_aug.append(label)
        
        # Add 'num_fake' noisy versions
        for _ in range(num_fake):
            # Create random noise
            noise = np.random.normal(0, noise_level, size=original_sample.shape)
            noisy_sample = original_sample + noise
            X_aug.append(noisy_sample)
            y_aug.append(label)
            
    return pd.DataFrame(X_aug, columns=X.columns), pd.Series(y_aug, name=y.name)

# 1. Load Data
df = pd.read_csv('train_data.csv')
X_raw = df.drop('class_name', axis=1)
y_raw = df['class_name']

print(f"Augmenting data: Adding 10 noisy samples per real ratio set...")
X, y = augment_with_noise(X_raw, y_raw)
print(f"New dataset size: {len(X)} samples")

# 2. Auto-generate Label Map
label_map = {label: idx for idx, label in enumerate(np.unique(y))}
print(f"Detected Labels: {label_map}")

# 3. Encode Data
y_encoded = y.map(label_map)
y_categorical = to_categorical(y_encoded)

# 4. Split Data (80% Train, 20% Test)
X_train, X_test, y_train, y_test = train_test_split(X, y_categorical, test_size=0.2, random_state=42)

# 5. Build Model
model = Sequential([
    tf.keras.Input(shape=(132,)),  # 33 landmarks * 4 (x, y, z, visibility)
    Dense(128, activation='relu'),
    Dropout(0.2),
    Dense(64, activation='relu'),
    Dropout(0.2),
    Dense(len(label_map), activation='softmax')
])

model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])

# 6. Train
history = model.fit(
    X_train, y_train,
    epochs=50,
    batch_size=32,
    validation_data=(X_test, y_test),
    verbose=1
)

loss, accuracy = model.evaluate(X_test, y_test, verbose=0)
print(f"\n✅ Final Accuracy: {accuracy*100:.2f}%")

# Save the Keras model for weight export
MODEL_PATH = 'posture_model.keras'
model.save(MODEL_PATH)
print(f"✅ Keras model saved to {MODEL_PATH}")

# @title Step 4: Convert to TFLite & Download
TFLITE_PATH = 'custom_posture_model.tflite'
LABEL_PATH = 'posture_labels.json'

print("Converting model...")

# 1. Convert directly from Keras model
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# 2. Save TFLite File
with open(TFLITE_PATH, 'wb') as f:
    f.write(tflite_model)

# 3. Save Label Map
with open(LABEL_PATH, 'w') as f:
    json.dump(label_map, f)

# 4. Files saved locally
print("Files saved locally:")
print(f"  - {TFLITE_PATH}")
print(f"  - {LABEL_PATH}")
print("✅ Done! Files are saved in the current directory.")
# @title Final Step: Test TFLite Model Accuracy
import tensorflow as tf
import numpy as np

# --- Configuration (Must match previous steps) ---
TFLITE_PATH = 'custom_posture_model.tflite'
# X_test, y_test, and label_map are assumed to be defined in Cell 3

# 1. Load the TFLite model content
try:
    with open(TFLITE_PATH, 'rb') as f:
        tflite_model_content = f.read()
except FileNotFoundError:
    print(f"❌ ERROR: TFLITE file not found at {TFLITE_PATH}. Ensure Step 4 was completed.")
    # Use a dummy variable or exit if file not found
    raise

# 2. Initialize the TFLite Interpreter
interpreter = tf.lite.Interpreter(model_content=tflite_model_content)
interpreter.allocate_tensors()

# Get input and output tensor details
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# Prepare test data: TFLite requires float32 input
test_data = X_test.values.astype(np.float32)

# Convert one-hot encoded y_test back to single index labels for comparison
true_labels_index = np.argmax(y_test, axis=1) 
inverse_label_map = {idx: label for label, idx in label_map.items()}

correct_predictions = 0
num_test_samples = len(test_data)

print(f"\n--- Running TFLite Inference on {num_test_samples} Test Samples ---")

for i in range(num_test_samples):
    # TFLite requires batch dimension (1, 132)
    input_tensor = np.expand_dims(test_data[i], axis=0)
    
    # Set the input tensor
    interpreter.set_tensor(input_details[0]['index'], input_tensor)
    
    # Run the model
    interpreter.invoke()
    
    # Get the output (probability vector)
    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    # The prediction is the index with the highest probability
    predicted_index = np.argmax(output_data)
    
    # Check if correct
    if predicted_index == true_labels_index[i]:
        correct_predictions += 1
    
    # Print results for the first few samples for inspection
    if i < 5:
        true_label_name = inverse_label_map[true_labels_index[i]]
        pred_label_name = inverse_label_map[predicted_index]
        confidence = output_data[0][predicted_index] * 100
        print(f"Sample {i+1}: True='{true_label_name}', Predicted='{pred_label_name}' ({confidence:.1f}% confidence)")

# Calculate Final Accuracy
tflite_accuracy = correct_predictions / num_test_samples

print("\n" + "=" * 50)
print(f"Trained Keras Model Accuracy (from Step 3): {model.history.history['val_accuracy'][-1]*100:.2f}%")
print(f"TFLite Model Accuracy (Inference Test):      {tflite_accuracy*100:.2f}%")
print("=" * 50)

if tflite_accuracy >= model.history.history['val_accuracy'][-1] - 0.01:
    print("✅ SUCCESS: TFLite accuracy closely matches Keras accuracy. The conversion is valid.")
else:
    print("⚠️ WARNING: TFLite accuracy dropped significantly. Check conversion settings.")
