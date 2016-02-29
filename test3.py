"""
Function: test3
Inputs: x_min, x_max
Outputs: y, x
y is random
y is positive
x is random and also in range[x_min, x_max]
"""
def test3(x_min, x_max):
    x = random()
    y = random()
    if(x < x_min):
       x = x_min;
    elif(x > x_max):
       x = x_max;
    if(y < 0):
       y = - y;
    return (y, x)