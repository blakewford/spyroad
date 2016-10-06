import Foundation

public func executeProgram() -> Void 
{
    _ = fetchN(1) // Throw away header
    while(fetchN(1) > 0)
    {
    }
}

var DEFAULT = "RouteId,Latitude,Longitude,DateTime,Heading,Speed,Distance,Elevation,Accuracy,\n45,30.341281,-97.727119,2016-08-03 19:45:26.000-0400,83.8,2.24,0.01,210.0,5,"
var ptr = UnsafeMutablePointer<CChar>.allocate(capacity: DEFAULT.characters.count)
ptr.initialize(from: DEFAULT, count: DEFAULT.characters.count)

engineInit()
loadProgram(ptr)
executeProgram()

ptr.deinitialize(count: DEFAULT.characters.count)
ptr.deallocate(capacity: DEFAULT.characters.count)
