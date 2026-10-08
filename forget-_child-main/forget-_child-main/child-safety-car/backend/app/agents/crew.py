"""CrewAI: 2 tác tử phối hợp — Image Analyzer (gọi VLM) -> Situation Analyzer (đánh giá nguy cơ).

Lưu ý: CrewAI/LLM chạy CHẬM (vài giây) nên nằm ở 'slow path'. Quyết định an toàn tức thời
do app/core/rules.py đảm nhiệm; kết quả của crew chỉ nâng mức tối đa tới WARNING và tạo thông điệp.
"""
import json
from collections.abc import Callable

from crewai import LLM, Agent, Crew, Process, Task
from crewai.tools import tool

from app.agents.vlm_client import VLMClient
from app.config import Settings
from app.schemas import EmotionResult, SituationDecision


def build_crew(
    cfg: Settings,
    vlm: VLMClient,
    get_child_jpeg: Callable[[], bytes | None],
    on_emotion: Callable[[EmotionResult], None],
) -> Crew:
    llm = LLM(model=cfg.crew_llm_model, base_url=cfg.crew_llm_base_url, temperature=0.1)

    @tool("analyze_child_emotion")
    def analyze_child_emotion() -> str:
        """Phân tích cảm xúc của trẻ từ ảnh mới nhất của camera trong xe.
        Trả về JSON gồm emotion, confidence, description."""
        jpeg = get_child_jpeg()
        if jpeg is None:
            return json.dumps({"emotion": "UNKNOWN", "confidence": 0, "description": "không có ảnh trẻ"})
        result = vlm.analyze_emotion(jpeg)
        on_emotion(result)                       # cập nhật ngay cho fast-path
        return result.model_dump_json()

    image_agent = Agent(
        role="Image Analyzer Agent",
        goal="Mô tả chính xác trạng thái cảm xúc hiện tại của trẻ trong xe.",
        backstory="Chuyên gia phân tích hình ảnh trẻ nhỏ, chỉ dựa trên dữ liệu từ tool.",
        tools=[analyze_child_emotion],
        llm=llm, allow_delegation=False, verbose=False,
    )
    situation_agent = Agent(
        role="Situation Analyzer Agent",
        goal="Đánh giá mức độ nguy cơ khi trẻ ở một mình trong xe và đề xuất thông điệp cho phụ huynh.",
        backstory="Chuyên gia an toàn trẻ em; thận trọng, không báo động sai, không bỏ sót nguy hiểm.",
        llm=llm, allow_delegation=False, verbose=False,
    )

    t_image = Task(
        description="Gọi tool analyze_child_emotion đúng MỘT lần rồi báo lại nguyên kết quả.",
        expected_output="JSON {emotion, confidence, description}",
        agent=image_agent,
    )
    t_situation = Task(
        description=(
            "Dữ liệu hiện tại: trẻ ở một mình {alone_seconds} giây; nhiệt độ cabin {temp_c}°C; "
            "số người lớn {adults}; số trẻ {children}. Kết hợp với cảm xúc của trẻ ở bước trước, "
            "đánh giá mức nguy cơ: 0=an toàn, 1=theo dõi, 2=cảnh báo, 3=khẩn cấp. "
            "Viết một câu thông báo tiếng Việt ngắn, rõ ràng cho phụ huynh."
        ),
        expected_output="JSON {level, reason, message_vi}",
        agent=situation_agent,
        context=[t_image],
        output_pydantic=SituationDecision,
    )
    return Crew(
        agents=[image_agent, situation_agent],
        tasks=[t_image, t_situation],
        process=Process.sequential,
        verbose=False,
    )
