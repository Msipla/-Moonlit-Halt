-- Create observations, candidate checks and ordered construction at an authorized site.
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

local function cellPosition(p)
  local seen = {}
  while type(p) == "table" and p.pos do
    if seen[p] then return nil end
    seen[p], p = true, p.pos
  end
  if type(p) ~= "table" then return nil end
  for _, axis in ipairs({"x", "y", "z"}) do
    local value = p[axis]
    if type(value) ~= "number" or value ~= value or math.abs(value) == math.huge then return nil end
  end
  -- Match the native Pos reader's block coordinates.
  return {x = math.floor(p.x), y = math.floor(p.y), z = math.floor(p.z)}
end

local function namespaced(id)
  return id:find(":", 1, true) and id or "minecraft:" .. id
end

local function matchesState(requested, actual)
  local id = requested:match("^%s*([^%s%[]+)")
  if not id or actual:match("^([^%[]+)") ~= namespaced(id) then return false end
  local properties = {}
  for key, value in actual:gmatch("([%w_]+)=([%w_%-]+)") do properties[key] = value end
  for key, value in requested:gmatch("([%w_]+)%s*=%s*([%w_%-]+)") do
    if properties[key] ~= value then return false end
  end
  return true
end

local function readBack(site, batch, applied)
  local byPosition, resolved, rejects = {}, {}, {}
  for _, cell in ipairs(batch) do byPosition[position(cellPosition(cell.pos))] = cell end
  for _, row in ipairs(applied.resolved) do resolved[position(row.pos)] = row.state end
  for first = 1, #batch, 16 do
    local positions = {}
    for i = first, math.min(first + 15, #batch) do positions[#positions + 1] = batch[i].pos end
    local observations = site.inspect_many(positions)
    assert(#observations == #positions, "Incomplete live readback")
    for _, row in ipairs(observations) do
      local key = position(row.pos)
      local cell, reading = byPosition[key], row.reading
      local reason
      if not matchesState(resolved[key] or cell.state, reading.state) then
        reason = "actual state " .. reading.state
      elseif cell.rpm and cell.rpm ~= reading.motor_setting then
        reason = "actual motor setting " .. tostring(reading.motor_setting)
      elseif cell.filter and namespaced(cell.filter) ~= reading.filter then
        reason = "actual filter " .. tostring(reading.filter)
      end
      if reason then rejects[#rejects + 1] = {pos = cell.pos, reason = reason} end
    end
  end
  return rejects
end

local function indexCells(cells)
  for index in pairs(cells) do
    assert(type(index) == "number" and index % 1 == 0 and index >= 1 and index <= #cells,
      "Site cells must form a dense array starting at 1")
  end
  local seen = {}
  for i = 1, #cells do
    local cell = cells[i]
    local pos = type(cell) == "table" and cellPosition(cell.pos)
    if not pos or type(cell.state) ~= "string" then
      return nil, {error = "Each site cell needs pos and state at index " .. i}
    end
    local key = position(pos)
    if seen[key] then
      return nil, {rejects = {{pos = pos, reason = "duplicate at indices " .. seen[key] .. " and " .. i}}}
    end
    seen[key] = i
  end
  return seen
end

local function vesselCheck(site, info, cells, index, options)
  local walk, drive = options.walk, options.drive
  assert(type(walk) == "table" and #walk >= 2 and #walk <= 32, "Supply 2..32 route feet positions")
  assert(type(drive) == "table" and #drive >= 2 and #drive <= 16, "Supply the 2..16 motor-to-mill positions")
  local rejects, hull = {}, {}
  for _, id in ipairs(info.hull) do hull[id] = true end

  local function projected(positions, path)
    local edits, used = {}, {}
    for _, raw in ipairs(positions) do
      local p = cellPosition(raw)
      assert(p, "Expected a position with x, y and z")
      for dy = path and -1 or 0, path and 2 or 0 do
        local i = index[position({x = p.x, y = p.y + dy, z = p.z})]
        if i and not used[i] then edits[#edits + 1], used[i] = cells[i], true end
      end
    end
    return edits
  end

  local function addPlan(result)
    for _, row in ipairs(result.changes.rejects) do rejects[#rejects + 1] = row end
    for _, row in ipairs(result.clearance) do rejects[#rejects + 1] = row end
  end

  local reads = {}
  for _, row in ipairs(site.inspect_many(drive)) do reads[position(row.pos)] = row.reading end
  for i, raw in ipairs(drive) do
    local p = cellPosition(raw)
    local key = position(p)
    local cell = cells[index[key]]
    local state = cell and cell.state or reads[key].state
    local id = state:match("^%s*([^%s%[]+)")
    local expected = i == 1 and "create:creative_motor" or
      i == #drive and "create:millstone" or "create:shaft"
    if not id or not hull[namespaced(id)] then
      rejects[#rejects + 1] = {pos = p, reason = "not a hull component: " .. state}
    elseif namespaced(id) ~= expected then
      rejects[#rejects + 1] = {pos = p, reason = "expected " .. expected .. " in the drive; got " .. state}
    end
    if i > 1 then
      local prev = cellPosition(drive[i - 1])
      if math.abs(prev.x - p.x) + math.abs(prev.y - p.y) + math.abs(prev.z - p.z) ~= 1 then
        rejects[#rejects + 1] = {pos = p, reason = "drive positions must be face-adjacent"}
      end
    end
  end
  if #rejects == 0 then
    addPlan(site.plan({cells = projected(drive), machines = drive}))
  end
  -- Keep one shared step so the native planner also checks each section boundary.
  for start = 1, #walk - 1, 15 do
    local segment = {}
    for i = start, math.min(start + 15, #walk) do segment[#segment + 1] = walk[i] end
    addPlan(site.plan({cells = projected(segment, true), walk = segment}))
  end
  local mill = reads[position(cellPosition(drive[#drive]))]
  if #cells == 0 and (not mill.rpm or mill.rpm == 0 or mill.power_status ~= "running") then
    rejects[#rejects + 1] = {pos = drive[#drive], reason = "live millstone is not running"}
  end
  return {ok = #rejects == 0, rejects = rejects, mill_rpm = mill.rpm}
end

---Check the proposed vessel path and motor-to-mill drive without editing the world.
---@param options table Required walk (feet coordinates) and drive (motor through shafts to mill). Optional cells are proposed edits. Omit cells to require live mill power.
---@return table Receipt with ok, rejects and current mill_rpm.
function M.check_vessel(options)
  local cells = options.cells or {}
  local index, problem = indexCells(cells)
  if not index then problem.ok = false; return problem end
  local site = engineering.site
  return vesselCheck(site, site.info(), cells, index, options)
end

---Execute an ordered list of site cells. Order supports before dependent blocks.
---The first unconfirmed range is next..through. A check failure made no writes to that range.
---After an apply/readback failure or interruption, inspect the uncertain range before resuming.
---@param cells table[] Ordered entries with pos and state. Optional expected, faces, rpm and filter use the native site edit format.
---@param vessel? table Optional walk and drive, as in check_vessel. Reject a blocked route or incompatible drive before any batch.
---@return table Receipt with stage, batches, confirmed, written, and the remaining range.
function M.construct(cells, vessel)
  assert(type(cells) == "table" and #cells > 0, "Supply an ordered list of site cells")
  local site = engineering.site
  local receipt = {batches = 0, confirmed = 0, written = 0, total = #cells, next = 1}
  local index, problem = indexCells(cells)
  if not index then
    receipt.stage, receipt.error, receipt.rejects = "plan", problem.error, problem.rejects
    return receipt
  end
  local info = site.info()
  if vessel then
    receipt.stage = "vessel"
    local ok, checked = pcall(vesselCheck, site, info, cells, index, vessel)
    if not ok then receipt.error = tostring(checked); return receipt end
    if not checked.ok then receipt.rejects = checked.rejects; return receipt end
  end
  local limit = info.batch_limit
  assert(type(limit) == "number" and limit >= 1 and limit % 1 == 0, "Invalid site batch_limit")
  receipt.batch_limit = limit
  for first = 1, #cells, limit do
    local last = math.min(first + limit - 1, #cells)
    local batch = {}
    for i = first, last do batch[#batch + 1] = cells[i] end
    receipt.next, receipt.through = first, last

    receipt.stage, receipt.backup = "check", nil
    local ok, checked = pcall(site.check, batch)
    if not ok then receipt.error = tostring(checked); return receipt end
    if #checked.rejects > 0 then receipt.rejects = checked.rejects; return receipt end

    receipt.stage = "apply"
    local applied
    ok, applied = pcall(site.apply, batch)
    if not ok then receipt.error, receipt.uncertain = tostring(applied), true; return receipt end

    receipt.stage, receipt.backup = "readback", applied.backup
    local rejects
    ok, rejects = pcall(readBack, site, batch, applied)
    if not ok then receipt.error, receipt.uncertain = tostring(rejects), true; return receipt end
    if #rejects > 0 then receipt.rejects, receipt.uncertain = rejects, true; return receipt end

    receipt.batches = receipt.batches + 1
    receipt.confirmed = last
    receipt.written = receipt.written + applied.cells
    receipt.next = last + 1
    print(("construction confirmed %d..%d; written=%d backup=%s"):format(
      first, last, applied.cells, applied.backup))
    for _, warning in ipairs(checked.warnings or {}) do print(warning) end
    for _, warning in ipairs(applied.warnings or {}) do print(warning) end
  end
  receipt.stage, receipt.through = "complete", nil
  return receipt
end

return M
