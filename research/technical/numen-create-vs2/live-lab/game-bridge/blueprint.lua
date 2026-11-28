-- Standalone geometry evaluator. Only the constants and functions in env reach the design.
-- The gable cross-section is adapted from MCBuild (34ce863), dsl/stdlib.py.
--[[
MIT License

Copyright (c) 2026 Harry

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
]]
local input = assert(io.open(arg[1], "rb"))
local source = input:read(65537) or ""
input:close()
assert(#source <= 65536, "Blueprint source exceeds 64 KiB")

local cells, seen = {}, {}
local lo, hi = {}, {}
for axis = 1, 3 do
  lo[axis], hi[axis] = assert(tonumber(arg[axis + 1])), assert(tonumber(arg[axis + 4]))
end

local function put(x, y, z, state, expected)
  for axis, n in ipairs({x, y, z}) do
    assert(math.type(n) == "integer" and n >= lo[axis] and n <= hi[axis], "Blueprint cell outside its site")
  end
  assert(math.type(x) and math.type(y) and math.type(z), "Blueprint position needs three integers")
  local at = string.format("%d,%d,%d", x, y, z)
  assert(not seen[at], "Duplicate blueprint position " .. at)
  local function safe(value)
    return type(value) == "string" and #value <= 220 and value:match("^[a-z0-9_:=,%[%.%]/%-]+$")
  end
  assert(safe(state) and (expected == nil or safe(expected)), "Invalid blueprint state at " .. at)
  assert(state ~= "minecraft:air" or (expected and expected ~= state), "Removal requires a non-air expected state")
  assert(#cells < 4096, "Too many blueprint cells")
  seen[at] = true
  cells[#cells + 1] = table.concat({at, state, expected or "minecraft:air"}, "\t")
end

local function box(x1, y1, z1, x2, y2, z2, state)
  for y = y1, y2 do
    for x = x1, x2 do
      for z = z1, z2 do put(x, y, z, state) end
    end
  end
end

local function gable(x1, z1, x2, z2, y, state, axis, overhang, endState)
  assert(axis == "x" or axis == "z", "Expected ridge axis x or z")
  assert(math.type(overhang) == "integer" and overhang >= 0 and overhang <= 2, "Invalid roof overhang")
  local acrossLo, acrossHi = axis == "x" and z1 or x1, axis == "x" and z2 or x2
  local alongLo, alongHi = axis == "x" and x1 or z1, axis == "x" and x2 or z2
  for across = acrossLo - overhang, acrossHi + overhang do
    local height = y + math.min(across - acrossLo + overhang, acrossHi + overhang - across)
    for along = alongLo - overhang, alongHi + overhang do
      if axis == "x" then put(along, height, across, state) else put(across, height, along, state) end
    end
    if endState and across >= acrossLo and across <= acrossHi then
      for yy = y, height - 1 do
        for _, along in ipairs({alongLo, alongHi}) do
          if axis == "x" then put(along, yy, across, endState) else put(across, yy, along, endState) end
        end
      end
    end
  end
end

local function orderedPairs(values)
  local keys = {}
  for key in pairs(values) do
    assert(type(key) == "number" or type(key) == "string", "Blueprint map keys must be numbers or strings")
    keys[#keys + 1] = key
  end
  table.sort(keys, function(a, b)
    if type(a) ~= type(b) then return type(a) == "number" end
    return a < b
  end)
  local i = 0
  return function()
    i = i + 1
    local key = keys[i]
    if key ~= nil then return key, values[key] end
  end
end

local env = {put = put, box = box, gable = gable, pairs = orderedPairs, ipairs = ipairs,
  math = {min = math.min, max = math.max, abs = math.abs, floor = math.floor, ceil = math.ceil}}
local program = assert(load(source, "blueprint", "t", env))
local steps = 0
debug.sethook(function()
  steps = steps + 1000
  assert(steps < 2000000, "Blueprint instruction budget reached")
end, "", 1000)
program()
debug.sethook()
assert(#cells > 0, "Blueprint returned no cells")
io.write(table.concat(cells, "\n"), "\n")
