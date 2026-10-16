import cv2
import numpy as np
import os
import zipfile

# 1. 이미지 로드 및 1080x1080 고해상도 규격으로 업스케일링
img = cv2.imread('ic_launcher.png')
img_res = cv2.resize(img, (1080, 1080), interpolation=cv2.INTER_CUBIC)

# 2. 배경 레이어 분리 (고래 캐릭터를 자연스럽게 지우고 주변 배경으로 채움)
# 마스크 생성: 고래 캐릭터가 있는 중심 영역 타깃팅
mask = np.zeros(img_res.shape[:2], np.uint8)
cv2.circle(mask, (540, 560), 320, 255, -1)
# 인페인팅 기술을 활용해 은은한 밤하늘 그라데이션과 스포트라이트를 보존하며 고래 제거
bg_layer = cv2.inpaint(img_res, mask, 25, cv2.INPAINT_TELEA)
cv2.imwrite('background.png', bg_layer)

# 3. 전경 레이어 추출 및 SVG 벡터 변환
# 고래와 음표의 푸른빛 외곽선과 형태를 정밀하게 따내기 위한 이진화 작업
gray = cv2.cvtColor(img_res, cv2.COLOR_BGR2GRAY)
_, thresh = cv2.threshold(gray, 45, 255, cv2.THRESH_BINARY)
contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

# 안드로이드 스튜디오에서 완벽하게 호환되는 SVG 파일 구조 설계
svg_height, svg_width = 1080, 1080
svg_chunks = [
    f'<svg xmlns="http://w3.org" viewBox="0 0 {svg_width} {svg_height}" width="{svg_width}" height="{svg_height}">',
    f'<rect width="100%" height="100%" fill="none"/>',
    f'<g fill="#4D86F7" stroke="#87B1FA" stroke-width="3" stroke-linejoin="round" stroke-linecap="round">'
]

# 검출된 고래와 음표의 좌표를 SVG 벡터 패스 데이터(d)로 변환
for count, contour in enumerate(contours):
    if cv2.contourArea(contour) > 500: # 미세한 배경 노이즈 점들은 제외
        path_data = []
        for i, point in enumerate(contour):
            x, y = point[0]
            if i == 0:
                path_data.append(f'M {x} {y}')
            else:
                path_data.append(f'L {x} {y}')
        path_data.append('Z')
        svg_chunks.append(f'  <path d="{" ".join(path_data)}" />')

svg_chunks.append('</g>\n</svg>')

with open('foreground.svg', 'w', encoding='utf-8') as f:
    f.write('\n'.join(svg_chunks))

# 4. 안드로이드 스튜디오 등록 편의를 위해 하나의 zip 파일로 패킹
with zipfile.ZipFile('whale_icon_assets.zip', 'w') as asset_zip:
    asset_zip.write('background.png')
    asset_zip.write('foreground.svg')

print("적응형 아이콘 분리 완료: background.png, foreground.svg 파일이 패킹되었습니다.")
