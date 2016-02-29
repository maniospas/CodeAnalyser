"""
Library: Programming
For any: k
b_desc[k]>=b_desc[k+1]
b_asc[k]<=b_asc[k+1]
b_desc rearranges a
b_asc rearranges a
"""
def sort(a):
   # sort a into b_desc in descending order
   b_desc = a.copy();
   for i in range(0, len(a)):
      for j in range(i + 1, len(a)):
         if(b_desc[i] < b_desc[j]):
            b_desc[i], b_desc[j] = b_desc[j], b_desc[i];
   # sort a into b_asc in ascending order
   b_asc = a.copy();
   for i in range(0, len(a)):
      for j in range(i + 1, len(a)):
         if(b_asc[i] > b_asc[j]):
            b_asc[i], b_asc[j] = b_asc[j], b_asc[i];
   return b_desc, b_asc

"""
Library: Mathematics
trionym coefficients a, b, c
trionym roots x1, x2
"""
def trionym(a, b, c):
   # calculate trionym determinant D
   D = b^2-4*a*c;
   x1 = None;
   x2 = None;
   # roots exist only if D>=0
   if(D>=0):
      x1 = (-b+D^0.5)/(2*a);
      x2 = (-b-D^0.5)/(2*a);
   return x1, x2
   
def random_number():
   # this function returns a random number
   return random()
   
def abs(x):
   # this function calculates the absolute value of a given number
   # the absolute value of a number is always positive
   if(x<0):
      x = -x;
   # x>=0
   return x;
   
def limit(x, x_min, x_max):
   # snaps the input x to range [x_min, x_max]
   return x_min+(x_max-x_min)*exp(-x^2);
   
def linear(a, b):
   #solves the linear equation with coefficients a, b;
   #root*a + b = 0
   root = None;
   if(a!=0):
      root = -b/a
   return root