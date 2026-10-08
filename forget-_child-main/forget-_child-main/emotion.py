import cv2
import torch
from PIL import Image

from transformers import (
    Qwen2_5_VLForConditionalGeneration,
    AutoProcessor
)
from qwen_vl_utils import process_vision_info


# =========================
# 1. LOAD MODEL
# =========================

MODEL_NAME = "Qwen/Qwen2.5-VL-3B-Instruct"

print("Loading VLM...")

model = Qwen2_5_VLForConditionalGeneration.from_pretrained(
    MODEL_NAME,
    torch_dtype="auto",
    device_map="auto"
)

processor = AutoProcessor.from_pretrained(
    MODEL_NAME
)

print("Model loaded!")


# =========================
# 2. ANALYZE IMAGE
# =========================

def analyze_emotion(frame):

    # OpenCV BGR -> RGB
    frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    # Convert to PIL
    image = Image.fromarray(frame_rgb)

    messages = [
        {
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "image": image
                },
                {
                    "type": "text",
                    "text": """
Analyze the visible person's apparent emotional expression.

Choose exactly ONE label:

HAPPY
ANGRY
NORMAL

Rules:
- HAPPY: smiling or clearly positive facial expression
- ANGRY: visible signs of anger such as frowning or tense expression
- NORMAL: no clear evidence of happiness or anger

Return ONLY one word:
HAPPY, ANGRY, or NORMAL.
"""
                }
            ]
        }
    ]

    # Create prompt
    text = processor.apply_chat_template(
        messages,
        tokenize=False,
        add_generation_prompt=True
    )

    image_inputs, video_inputs = process_vision_info(
        messages
    )

    inputs = processor(
        text=[text],
        images=image_inputs,
        videos=video_inputs,
        padding=True,
        return_tensors="pt"
    )

    inputs = inputs.to(model.device)

    # Generate
    with torch.no_grad():

        generated_ids = model.generate(
            **inputs,
            max_new_tokens=10
        )

    # Remove input tokens
    generated_ids_trimmed = [
        out_ids[len(in_ids):]
        for in_ids, out_ids
        in zip(inputs.input_ids, generated_ids)
    ]

    result = processor.batch_decode(
        generated_ids_trimmed,
        skip_special_tokens=True
    )[0]

    result = result.strip().upper()

    # Force valid result
    if "HAPPY" in result:
        return "HAPPY"

    if "ANGRY" in result:
        return "ANGRY"

    return "NORMAL"


# =========================
# 3. OPEN WEBCAM
# =========================

cap = cv2.VideoCapture(0)

if not cap.isOpened():
    print("Cannot open webcam")
    exit()


emotion = "NORMAL"

# Analyze every 2 seconds
fps = 30
interval = 2 * fps

counter = 0


# =========================
# 4. MAIN LOOP
# =========================

while True:

    ret, frame = cap.read()

    if not ret:
        break

    counter += 1

    # Analyze periodically
    if counter >= interval:

        print("Analyzing...")

        emotion = analyze_emotion(frame)

        print("Emotion:", emotion)

        counter = 0


    # Display result
    cv2.putText(
        frame,
        f"Emotion: {emotion}",
        (30, 50),
        cv2.FONT_HERSHEY_SIMPLEX,
        1.2,
        (0, 255, 0),
        3
    )

    cv2.imshow(
        "VLM Emotion Detection",
        frame
    )


    # Press Q to quit
    if cv2.waitKey(1) & 0xFF == ord("q"):
        break


cap.release()
cv2.destroyAllWindows()