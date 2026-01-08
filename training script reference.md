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
from sklearn.utils.class_weight import compute_class_weight
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, Dropout
from tensorflow.keras.utils import to_categorical
from tensorflow.keras.callbacks import EarlyStopping

# ============================================================================
# Configuration: Raw Landmark Features (132 Features)
# ============================================================================
NUM_FEATURES = 132  # 33 landmarks * (x, y, z, visibility)

def get_raw_features(landmarks):
    """
    Args:
        landmarks: Normalized pose landmarks (0.0 to 1.0).
    Returns:
        A flat list of 132 features (x, y, z, visibility for each).
    """
    features = []
    for lm in landmarks:
        # We now include visibility. This helps the model identify 
        # occlusions common in desk setups (e.g., desk blocking elbows).
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

    # Write Header (class_name + 132 generic 'feat' columns)
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

            # USE NORMALIZED LANDMARKS (pose_landmarks)
            if results.pose_landmarks:
                lm_list = results.pose_landmarks.landmark
                
                # Generate 132 raw features
                features = get_raw_features(lm_list)
                
                row = [class_name] + features
                csv_writer.writerow(row)

print(f"✅ Data saved to {output_csv_path}")

```
# @title Step 3: Train Neural Network (Single Frame, 132 Features)
# --- Augmentation (lighter), Stratified split, Normalization, Early stopping ---
def augment_with_noise(X, y, num_fake=3, noise_level=0.003):
    X_aug = []
    y_aug = []
    for i in range(len(X)):
        original_sample = X.iloc[i].values
        label = y.iloc[i]
        # Original
        X_aug.append(original_sample)
        y_aug.append(label)
        # Noisy copies
        for _ in range(num_fake):
            noise = np.random.normal(0, noise_level, size=original_sample.shape)
            X_aug.append(original_sample + noise)
            y_aug.append(label)
    return pd.DataFrame(X_aug, columns=X.columns), pd.Series(y_aug, name=y.name)

# 1) Load data
df = pd.read_csv('train_data.csv')
X_raw = df.drop('class_name', axis=1)
y_raw = df['class_name']

# 2) Label map
label_map = {label: idx for idx, label in enumerate(np.unique(y_raw))}
print(f"Detected Labels: {label_map}")

# 3) Stratified split on raw (no aug yet)
X_train_raw, X_test_raw, y_train_raw, y_test_raw = train_test_split(
    X_raw, y_raw, test_size=0.2, random_state=42, stratify=y_raw
)

# 4) Normalize using train statistics
train_mean = X_train_raw.mean()
train_std = X_train_raw.std().replace(0, 1e-6)
X_train_norm = (X_train_raw - train_mean) / train_std
X_test_norm = (X_test_raw - train_mean) / train_std

# 5) Augment training only
print("Augmenting train set with light Gaussian noise...")
X_train_aug, y_train_aug = augment_with_noise(X_train_norm, y_train_raw)

# 6) Encode labels
y_train_idx = y_train_aug.map(label_map)
y_test_idx = y_test_raw.map(label_map)
y_train = to_categorical(y_train_idx)
y_test = to_categorical(y_test_idx)

# 7) Class weights (optional but helpful if imbalance)
class_weights = compute_class_weight(
    class_weight='balanced',
    classes=np.unique(y_train_idx),
    y=y_train_idx.values
)
class_weights = {i: w for i, w in enumerate(class_weights)}
print(f"Class weights: {class_weights}")

# 8) Model
model = Sequential([
    tf.keras.Input(shape=(NUM_FEATURES,)),  # 132 features
    Dense(256, activation='relu'),
    Dropout(0.3),
    Dense(128, activation='relu'),
    Dropout(0.3),
    Dense(len(label_map), activation='softmax')
])

model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])

early_stop = EarlyStopping(patience=5, restore_best_weights=True, monitor='val_loss')

# 9) Train
history = model.fit(
    X_train_aug, y_train,
    epochs=50,
    batch_size=32,
    validation_data=(X_test_norm, y_test),
    class_weight=class_weights,
    callbacks=[early_stop],
    verbose=1
)

loss, accuracy = model.evaluate(X_test_norm, y_test, verbose=0)
print(f"\n✅ Final Accuracy: {accuracy*100:.2f}%")

# 10) Save normalization stats for inference
norm_stats = {
    "mean": train_mean.tolist(),
    "std": train_std.tolist(),
    "label_map": label_map
}
with open('norm_stats.json', 'w') as f:
    json.dump(norm_stats, f)
print("✅ Saved normalization stats to norm_stats.json")

# Save the Keras model for weight export
MODEL_PATH = 'posture_model.keras'
model.save(MODEL_PATH)
print(f"✅ Keras model saved to {MODEL_PATH}")
```

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
