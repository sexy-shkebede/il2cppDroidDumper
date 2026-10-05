Disassembler helper scripts copied into every dump (unchanged from Il2CppDumper 6.7.46):
  ida.py / ida_py3.py / ida_with_struct*.py   - IDA
  ghidra.py / ghidra_with_struct.py           - Ghidra
  ghidra_wasm.py                              - Ghidra + ghidra-wasm-plugin
  il2cpp_header_to_ghidra.py                  - import il2cpp.h into Ghidra
  il2cpp_header_to_binja.py / Il2CppBinaryNinja/ - Binary Ninja
  hopper-py3.py                               - Hopper
Load script.json (and il2cpp.h for the *_with_struct variants) with these in your tool.
