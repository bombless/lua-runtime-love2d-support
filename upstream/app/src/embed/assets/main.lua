local width, height = 0, 0
local orb = { x = 180, y = 160, vx = 185, vy = 135, radius = 34 }
local touch = { x = 0, y = 0, active = false }
local elapsed = 0
local frameRate = 0

local function moveOrbTo(x, y)
    orb.x = x
    orb.y = y
    orb.vx = (love.math.random(0, 1) == 0 and -1 or 1) * 185
    orb.vy = (love.math.random(0, 1) == 0 and -1 or 1) * 135
end

function love.load()
    love.graphics.setDefaultFilter("linear", "linear")
    love.graphics.setLineStyle("smooth")
    love.graphics.setBackgroundColor(0.055, 0.075, 0.12, 1)
    love.math.setRandomSeed(os.time())
    width, height = love.graphics.getDimensions()
end

function love.update(dt)
    dt = math.min(dt, 0.05)
    elapsed = elapsed + dt
    frameRate = love.timer.getFPS()

    orb.x = orb.x + orb.vx * dt
    orb.y = orb.y + orb.vy * dt

    if orb.x < orb.radius or orb.x > width - orb.radius then
        orb.vx = -orb.vx
        orb.x = math.max(orb.radius, math.min(width - orb.radius, orb.x))
    end
    if orb.y < orb.radius or orb.y > height - orb.radius then
        orb.vy = -orb.vy
        orb.y = math.max(orb.radius, math.min(height - orb.radius, orb.y))
    end
end

function love.draw()
    width, height = love.graphics.getDimensions()

    -- Subtle grid makes resizing and the active drawable area easy to see.
    love.graphics.setColor(0.16, 0.21, 0.29, 0.55)
    for x = 0, width, 48 do
        love.graphics.line(x, 0, x, height)
    end
    for y = 0, height, 48 do
        love.graphics.line(0, y, width, y)
    end

    -- Animated LÖVE-rendered orb and its motion trail.
    love.graphics.setColor(0.18, 0.72, 0.96, 0.18)
    love.graphics.circle("fill", orb.x, orb.y, orb.radius * 1.65)
    love.graphics.setColor(0.24, 0.82, 1.0, 1)
    love.graphics.circle("fill", orb.x, orb.y, orb.radius)
    love.graphics.setColor(0.78, 0.95, 1.0, 0.9)
    love.graphics.circle("fill", orb.x - 10, orb.y - 11, 8)

    if touch.active then
        love.graphics.setColor(1, 0.78, 0.25, 0.9)
        love.graphics.circle("line", touch.x, touch.y, 24 + math.sin(elapsed * 8) * 5)
        love.graphics.circle("fill", touch.x, touch.y, 5)
    end

    local textX = 22
    local textWidth = math.max(140, width - 44)
    local font = love.graphics.getFont()
    local lineHeight = font:getHeight() + 5
    local textY = 112

    love.graphics.setColor(0.92, 0.96, 1, 1)
    love.graphics.print("LÖVE2D Companion", textX, textY)
    textY = textY + lineHeight + 5

    love.graphics.setColor(0.68, 0.76, 0.88, 1)
    local function drawWrapped(text)
        local _, lines = font:getWrap(text, textWidth)
        love.graphics.printf(text, textX, textY, textWidth, "left")
        textY = textY + math.max(1, #lines) * lineHeight + 4
    end

    drawWrapped("Real LÖVE 11.5 · LuaJIT · Android native engine")
    drawWrapped("Tap anywhere to move the orb · Rotate or resize the screen")
    drawWrapped(string.format("FPS: %d   Viewport: %d × %d", frameRate, width, height))
    drawWrapped("Binder status is shown in the native badge at the top-left.")
end

function love.touchpressed(id, x, y, dx, dy, pressure)
    touch.x, touch.y, touch.active = x, y, true
    moveOrbTo(x, y)
end

function love.touchmoved(id, x, y, dx, dy, pressure)
    touch.x, touch.y = x, y
end

function love.touchreleased(id, x, y, dx, dy, pressure)
    touch.active = false
end

function love.mousepressed(x, y, button, istouch, presses)
    touch.x, touch.y, touch.active = x, y, true
    moveOrbTo(x, y)
end

function love.mousereleased(x, y, button, istouch, presses)
    touch.active = false
end

function love.resize(w, h)
    width, height = w, h
    orb.x = math.max(orb.radius, math.min(width - orb.radius, orb.x))
    orb.y = math.max(orb.radius, math.min(height - orb.radius, orb.y))
end

function love.keypressed(key)
    if key == "escape" or key == "back" then
        love.event.quit()
    end
end
