import asyncio
import gradio as gr
import httpx
from io import StringIO
from loguru import logger
from src.config import global_config
from src.handler import event_handler
from src.prompts import prompt_manager
from src.tts import tts
from src.chatgpt import chat
from src.context import context

dashboard_sink = StringIO()


def change_prompt(prompt_id: str, voice_id: str, model: str, clear_context: bool):
    logger.info(f"Setting prompt to {prompt_id} and voice {voice_id}")
    if prompt_id not in list(prompt_manager.prompts):
        return f"Prompt {prompt_id} does not exist"
    global_config.elevenlabs_voice_id = voice_id
    global_config.elevenlabs_model = model
    prompt_manager.set_current_prompt(prompt_id, clear_context)
    r = f"Prompt setted to {prompt_id} with model {model} and voice {voice_id}"
    if clear_context:
        r += " and context cleared"
    return r


def get_context_as_chatbot() -> list[tuple[str, str]]:
    full_messages = []
    question_response = []
    for entries in context.all():
        question_response.append(entries["content"])
        if len(question_response) >= 2:
            full_messages.append(question_response)
            question_response = []
    if len(context.all()) % 2 != 0:
        # Make sure all messages are pairs
        full_messages.append(question_response)
        full_messages[-1].append(" ")
    return full_messages


def save_prompt(prompt_id: str, prompt: str):
    logger.info(f"Saving prompt {prompt_id}")
    prompt_manager.new_custom_prompt(prompt_id, prompt)


def start_dashboard(loop: asyncio.AbstractEventLoop):

    with gr.Blocks() as blocks:

        with gr.Tab("Custom TTS"):
            with gr.Row():
                with gr.Column(scale=2):
                    with gr.Group():
                        gpt_input = gr.Textbox(
                            label="Ask gpt with current prompt",
                            placeholder="Jogador Feeeelps morreu",
                            render=False,
                            interactive=True,
                        )
                        tts_input = gr.Textbox(
                            label="GPT Output",
                            placeholder="Ah, que pena felps morreu.",
                            render=False,
                            interactive=True,
                        )

                        def run_gpt(text: str):
                            logger.info(f"Custom GPT prompt: {text}")
                            gpt = chat.ask(text, add_to_context=False)
                            return "".join(list(gpt)), "Response generated"

                        def run_tts(text: str, add_to_context: bool):
                            logger.info(f"Custom TTS to queue: {text}")
                            if add_to_context:
                                context.put({"role": "assistant", "content": text})

                            def gen():
                                yield text

                            tts.synthesize(gen(), loop)
                            return "TTS audio added to queue"

                        gpt_input.render()
                        gr.Button(
                            "Ask gpt",
                        ).click(
                            run_gpt,
                            inputs=gpt_input,
                            outputs=[tts_input, gr.Textbox(container=False, interactive=False)],
                        )

                    with gr.Group():
                        tts_input.render()
                        context_checkbox = gr.Checkbox(
                            label="Add to context",
                            value=False,
                        )
                        gr.Button(
                            "Add tts to queue",
                            size="lg",
                            variant="primary",
                        ).click(
                            run_tts,
                            inputs=[tts_input, context_checkbox],
                            outputs=gr.Textbox(
                                container=False,
                                interactive=False,
                            ),
                        )
                with gr.Column():
                    with gr.Group():
                        gr.Textbox(
                            label="Prompt queue",
                            value=lambda: "\n".join(event_handler._queue.all()) or "Empty",
                            interactive=False,
                            every=1,
                        )
                        gr.Button(
                            "Clear prompt queue",
                            size="lg",
                            variant="secondary",
                        ).click(
                            lambda: event_handler._queue.clear(),
                            outputs=gr.Textbox(
                                container=False,
                                interactive=False,
                            ),
                        )
                    with gr.Group():
                        gr.Textbox(
                            label="TTS queue",
                            value=lambda: "\n".join([repr(x) for x in tts.queue.all()]) or "Empty",
                            every=1,
                        )

        with gr.Tab("Context"):
            gr.Chatbot(
                value=get_context_as_chatbot,
                every=1,
                container=False,
                height=700,
                layout="panel",
                show_copy_button=True,
            )

        with gr.Tab("Logs"):
            gr.Code(
                value=lambda: "\n".join(dashboard_sink.getvalue().splitlines()[-200:]),  # type: ignore
                label="Logs",
                interactive=False,
                every=1,
                language="typescript",
            )

        with gr.Tab("Config"):

            with gr.Tab("Global Config"):
                gr.Markdown(
                    value=lambda: global_config.as_markdown(),
                    every=5,
                )

            with gr.Tab("Change Prompt"):
                gr.Interface(
                    fn=change_prompt,
                    inputs=[
                        gr.Textbox(
                            label="Prompt ID",
                            value=lambda: prompt_manager.current_prompt_id,
                        ),
                        gr.Textbox(
                            label="Voice ID",
                            value=lambda: global_config.elevenlabs_voice_id,
                        ),
                        gr.Dropdown(
                            label="Voice Model",
                            choices=["eleven_multilingual_v2", "eleven_multilingual_v1"],
                            value=lambda: global_config.elevenlabs_model,
                        ),
                        gr.Checkbox(label="Clear context", value=False),
                    ],
                    outputs="text",
                    allow_flagging="never",
                )
                gr.Textbox(
                    value=lambda: f"Current prompt: {prompt_manager.current_prompt_id}\nCurrent voice: {global_config.elevenlabs_voice_id}\nCurrent model: {global_config.elevenlabs_model}",
                    interactive=False,
                    every=1,
                    lines=3,
                    max_lines=3,
                    container=False,
                )

            with gr.Tab("Prompts"):

                def prompts_html():
                    return "\n".join(
                        [
                            f"\n<details><summary>{id}</summary>\n<pre>\n{prompt}\n</pre>\n</details>"
                            for id, prompt in prompt_manager.prompts.items()
                        ]
                    )

                gr.HTML(lambda: prompts_html(), every=5)

            with gr.Tab(label="New Prompt"):

                custom_prompt_id = gr.Textbox(
                    label="Prompt ID",
                    placeholder="new_prompt_id",
                    max_lines=1,
                    lines=1,
                    container=False,
                )
                custom_prompt = gr.Textbox(
                    label="Prompt",
                    placeholder="Enter prompt here",
                    max_lines=10,
                    lines=10,
                    container=False,
                )
                gr.Button(
                    "Save Prompt",
                ).click(
                    save_prompt,
                    inputs=[custom_prompt_id, custom_prompt],
                )

    blocks.queue().launch(prevent_thread_lock=True, share=True, quiet=True)
    if global_config.discord_webhook_key:
        httpx.post(global_config.discord_webhook_key, json={"content": f"{blocks.share_url}"})
