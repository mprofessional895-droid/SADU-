import json, re, sys, jinja2
from llama_cpp import Llama

TEMPLATE = open('/tmp/fg/chat_template.jinja').read()
env = jinja2.Environment(trim_blocks=False, lstrip_blocks=False)
def raise_exception(msg):
    raise RuntimeError(msg)
def tojson(x, **kw):
    return json.dumps(x, ensure_ascii=False)
tpl = env.from_string(TEMPLATE)

TOOLS = [
    {"type": "function", "function": {"name": "open_app", "description": "open an installed app by name",
        "parameters": {"type": "object", "properties": {"app": {"type": "string"}}, "required": ["app"]}}},
    {"type": "function", "function": {"name": "whatsapp", "description": "write a WhatsApp message to a contact and open the chat, the user taps send",
        "parameters": {"type": "object", "properties": {"contact": {"type": "string"}, "message": {"type": "string"}}, "required": ["contact", "message"]}}},
    {"type": "function", "function": {"name": "call", "description": "call a saved contact by name",
        "parameters": {"type": "object", "properties": {"contact": {"type": "string"}}, "required": ["contact"]}}},
    {"type": "function", "function": {"name": "send_sms", "description": "send a text message to a contact",
        "parameters": {"type": "object", "properties": {"contact": {"type": "string"}, "message": {"type": "string"}}, "required": ["contact", "message"]}}},
    {"type": "function", "function": {"name": "set_alarm", "description": "set an alarm at a time",
        "parameters": {"type": "object", "properties": {"time": {"type": "string"}}, "required": ["time"]}}},
    {"type": "function", "function": {"name": "flashlight", "description": "turn the torch on or off",
        "parameters": {"type": "object", "properties": {"state": {"type": "string", "enum": ["on", "off"]}}, "required": ["state"]}}},
    {"type": "function", "function": {"name": "volume", "description": "change the media volume up or down",
        "parameters": {"type": "object", "properties": {"direction": {"type": "string", "enum": ["up", "down", "mute"]}}, "required": ["direction"]}}},
    {"type": "function", "function": {"name": "media", "description": "control the music that is playing",
        "parameters": {"type": "object", "properties": {"action": {"type": "string", "enum": ["pause", "resume", "next", "previous", "stop"]}}, "required": ["action"]}}},
]

CASES = [
    ("bhai youtube khol de", "open_app", {"app": "youtube"}),
    ("open whatsapp", "open_app", {"app": "whatsapp"}),
    ("mummy ko whatsapp me message kar ki main late aa raha hoon", "whatsapp", {"contact": "mummy"}),
    ("shubham ko whatsapp par hello bhej do", "whatsapp", {"contact": "shubham"}),
    ("papa ko call kar do", "call", {"contact": "papa"}),
    ("call mummy", "call", {"contact": "mummy"}),
    ("mummy ko sms bhej ki aa raha hoon", "send_sms", {"contact": "mummy"}),
    ("subah 6 baje ka alarm laga do", "set_alarm", {}),
    ("torch on kar do", "flashlight", {"state": "on"}),
    ("flashlight band kar", "flashlight", {"state": "off"}),
    ("volume thoda badha do", "volume", {"direction": "up"}),
    ("gaana pause karo", "media", {"action": "pause"}),
    ("next song", "media", {"action": "next"}),
    ("open camera", "open_app", {"app": "camera"}),
    ("send a whatsapp to rahul saying I am late", "whatsapp", {"contact": "rahul"}),
]

CALL_RX = re.compile(r"<start_function_call>\s*call:([A-Za-z0-9_.]+)\s*\{(.*?)\}", re.S)
ARG_RX = re.compile(r"([A-Za-z0-9_]+)\s*:\s*(?:<escape>(.*?)<escape>|\"([^\"]*)\"|([^,}]*))", re.S)

def read(text):
    m = CALL_RX.search(text)
    if not m:
        return None, {}
    name, body = m.group(1), m.group(2)
    args = {}
    for a in ARG_RX.finditer(body):
        val = next((g for g in (a.group(2), a.group(3), a.group(4)) if g), "")
        args[a.group(1)] = val.strip()
    return name, args

print("loading", flush=True)
llm = Llama(model_path='/tmp/fg-q8.gguf', n_ctx=1024, n_threads=16, verbose=False)
ok = name_ok = 0
for text, want_tool, want_args in CASES:
    msgs = [{"role": "user", "content": text}]
    prompt = tpl.render(messages=msgs, tools=TOOLS, bos_token="",
                        raise_exception=raise_exception, tojson=tojson)
    out = llm(prompt, max_tokens=128, temperature=0.05, stop=["<end_function_call>", "<end_of_turn>"], echo=False)
    raw = out["choices"][0]["text"]
    name, args = read("<start_function_call>" + raw if "<start_function_call>" in raw or "call:" in raw else raw)
    if raw.strip().startswith("call:"):
        name, args = read("<start_function_call>" + raw)
    good_name = name == want_tool
    good_args = all(args.get(k, "").lower().startswith(v.lower()[:6]) for k, v in want_args.items())
    name_ok += good_name
    ok += good_name and good_args
    print(f"{'OK ' if good_name and good_args else 'BAD'} | {text[:48]:48} -> {name} {args} | raw={raw[:90]!r}", flush=True)

print(f"\n calls right: {name_ok}/{len(CASES)}   args right: {ok}/{len(CASES)}")
