import tkinter as tk
from tkinter import scrolledtext, messagebox
import requests
from bs4 import BeautifulSoup
import chardet
import os

def extract_text():
    url = entry_url.get().strip()
    if not url:
        messagebox.showwarning("입력 오류", "URL을 입력하세요.")
        return

    try:
        # 페이지 요청
        response = requests.get(url, timeout=10)
        # 인코딩 자동 감지
        detected = chardet.detect(response.content)
        response.encoding = detected["encoding"] or "utf-8"

        # HTML 파싱
        soup = BeautifulSoup(response.text, "html.parser")

        # script, style 제거
        for script in soup(["script", "style"]):
            script.decompose()

        # 텍스트 추출
        text = soup.get_text(separator="\n")
        clean_text = "\n".join(
            [line.strip() for line in text.splitlines() if line.strip()]
        )

        # 텍스트창에 표시
        text_box.delete(1.0, tk.END)
        text_box.insert(tk.END, clean_text)

        # 파일로 저장
        with open("result.txt", "w", encoding="utf-8") as f:
            f.write(clean_text)

        messagebox.showinfo("완료", "텍스트 추출이 완료되었습니다.\n(result.txt로 저장됨)")
    except Exception as e:
        messagebox.showerror("오류 발생", f"에러: {e}")

# GUI 구성
root = tk.Tk()
root.title("Web Text Extractor")
root.geometry("700x500")

frame_top = tk.Frame(root)
frame_top.pack(pady=10)

label_url = tk.Label(frame_top, text="URL 입력:")
label_url.pack(side=tk.LEFT)

entry_url = tk.Entry(frame_top, width=60)
entry_url.pack(side=tk.LEFT, padx=5)

btn_extract = tk.Button(frame_top, text="텍스트 추출", command=extract_text)
btn_extract.pack(side=tk.LEFT, padx=5)

text_box = scrolledtext.ScrolledText(root, wrap=tk.WORD, width=80, height=25)
text_box.pack(padx=10, pady=10)

root.mainloop()
