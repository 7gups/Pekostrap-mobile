local CorePackages = game:GetService("CorePackages")
local ArgCheck = require(CorePackages.ArgCheck)
local Logging = require(CorePackages.Logging)
local UIBlox = require(CorePackages.UIBlox)
local validateTheme = UIBlox.Style.Validator.validateTheme

-- Pekostrap "Force dark mode": always picks the dark theme.
-- Falls back to the original lookup if the map has no dark entry.
return function (themeName, defaultTheme, themeMap)
	local mappedTheme = themeMap["dark"]

	if mappedTheme == nil then
		if themeName ~= nil and #themeName > 0 then
			mappedTheme = themeMap[string.lower(themeName)]
		end
		if mappedTheme == nil then
			mappedTheme = themeMap[defaultTheme]
			Logging.warn(string.format("Unrecognized theme name: `%s`", tostring(themeName)))
		end
	end
	ArgCheck.assert(validateTheme(mappedTheme))
	return mappedTheme
end
