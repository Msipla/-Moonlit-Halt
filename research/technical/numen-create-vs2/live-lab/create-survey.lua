-- Read-only Create observations and candidate checks for a local change.
local M = {}

local function position(p)
  return p.x .. "," .. p.y .. "," .. p.z
end

local function preview(cells)
  assert(#cells > 0 and #cells <= 24, "Supply 1 to 24 local candidate cells")
  local diff = numen.build.diff(cells)
  local rows = {
    ("candidate left=%d reach=%d far=%d unsupported=%d skipped=%d liquid=%d"):format(
      diff.left, diff.reach, diff.far, diff.unheld, diff.skipped, diff.liquid)
  }
  for _, p in ipairs(diff.dig) do
    rows[#rows + 1] = "dig " .. position(p)
  end
  for _, c in ipairs(cells) do
    if c.name == "minecraft:air" or c.name == "air" then
      local b = numen.scan.block(c.pos)
      if not b.is_air then rows[#rows + 1] = "occupied " .. position(c.pos) .. " " .. b.name end
    end
  end
  return table.concat(rows, "\n")
end

local function edgeOpenings(y, xmin, xmax, zmin, zmax)
  local openings = {}
  for x = xmin, xmax do
    for z = zmin, zmax do
      if x == xmin or x == xmax or z == zmin or z == zmax then
        local feet = {x = x, y = y + 1, z = z}
        if numen.scan.block(feet).is_air and numen.scan.block({x = x, y = y + 2, z = z}).is_air then
          openings[#openings + 1] = position(feet)
        end
      end
    end
  end
  local count = #openings
  while #openings > 8 do openings[#openings] = nil end
  return "two-high floor-edge openings " .. count ..
    (#openings > 0 and ": " .. table.concat(openings, " ") or "")
end

local function floorExtent(floor, radius)
  local id = numen.scan.block(floor).name
  if id == "minecraft:air" then return "floor tile is air at " .. position(floor) end
  for _, group in ipairs(numen.scan.blocks(id, {radius = radius})) do
    local xmin, xmax, zmin, zmax = floor.x, floor.x, floor.z, floor.z
    local count, anchored = 0, false
    for _, part in ipairs(group.blocks) do
      local p = part.pos
      if p.y == floor.y then
        anchored = anchored or (p.x == floor.x and p.z == floor.z)
        xmin, xmax = math.min(xmin, p.x), math.max(xmax, p.x)
        zmin, zmax = math.min(zmin, p.z), math.max(zmax, p.z)
        count = count + 1
      end
    end
    if anchored then
      local receipt = ("floor %s y=%d x=%d..%d z=%d..%d count=%d"):format(
        id, floor.y, xmin, xmax, zmin, zmax, count)
      local width, depth = xmax - xmin + 1, zmax - zmin + 1
      if count == width * depth and width >= 3 and depth >= 3 and width + depth <= 18 then
        receipt = receipt .. "\n" .. edgeOpenings(floor.y, xmin, xmax, zmin, zmax)
      end
      return receipt
    end
  end
  return "floor tile not in the observed clusters at " .. position(floor)
end

---@class CreateSurveyOptions
---@field ids? string[] Component IDs to find near the agent.
---@field load? string Load ID used as the floor reference when anchor is omitted.
---@field anchor? Pos Observed machine position from which to look down for a floor.
---@field floor? Pos Observed floor tile, if already known.
---@field radius? integer Search radius around the agent, default 16.
---@field cells? table[] Agent's own local candidate cells {name, pos}. When provided, only check this list.

---Read parts, orientation and local floor, or preview the candidate cells. No world writes.
---@param options CreateSurveyOptions
---@return string
function M.observe(options)
  if options.cells then return preview(options.cells) end
  local ids = options.ids or {}
  assert(#ids > 0, "Supply component IDs in ids, or a candidate list in cells")
  local radius = options.radius or 16
  local rows, names, load, count = {}, {}, nil, 0
  for _, id in ipairs(ids) do names[id] = true end
  for _, group in ipairs(numen.scan.blocks(ids, {radius = radius})) do
    for _, part in ipairs(group.blocks) do
      local p = part.pos
      if part.name == options.load and not load then load = p end
      if count < 16 then
        local state = numen.scan.block(p).properties or {}
        rows[#rows + 1] = part.name .. " " .. position(p) .. " " .. (state.axis or state.facing or "-")
      end
      count = count + 1
    end
  end
  rows[#rows + 1] = "parts " .. count .. (count > 16 and " (first 16 shown)" or "")
  local floor, anchor = options.floor, options.anchor or load
  if not floor and anchor then
    for depth = 1, 3 do
      local p = {x = anchor.x, y = anchor.y - depth, z = anchor.z}
      local b = numen.scan.block(p)
      if b.is_solid and not names[b.name] then floor = p; break end
    end
  end
  rows[#rows + 1] = floor and floorExtent(floor, radius) or "no floor observed"
  return table.concat(rows, "\n")
end

return M
